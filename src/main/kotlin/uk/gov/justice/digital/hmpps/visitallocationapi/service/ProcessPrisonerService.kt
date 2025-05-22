package uk.gov.justice.digital.hmpps.visitallocationapi.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.IncentivesClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.VisitDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class ProcessPrisonerService(
  private val prisonerSearchClient: PrisonerSearchClient,
  private val incentivesClient: IncentivesClient,
  private val prisonerDetailsService: PrisonerDetailsService,
  private val prisonerRetryService: PrisonerRetryService,
  private val changeLogService: ChangeLogService,
  private val telemetryClient: TelemetryClient,
  @Value("\${max.visit-orders:26}") val maxAccumulatedVisitOrders: Int,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun processPrisonerVisitOrderUsage(visit: VisitDto) {
    val dpsPrisonerDetails: PrisonerDetails = prisonerDetailsService.getPrisonerDetails(visit.prisonerId)
      ?: prisonerDetailsService.createPrisonerDetails(visit.prisonerId, LocalDate.now().minusDays(14), null)

    // Find the oldest PVO to use. If none exists, find the oldest VO to use.
    val selected: VisitOrder? = dpsPrisonerDetails.visitOrders
      .asSequence()
      .filter { it.status == VisitOrderStatus.AVAILABLE }
      .filter { it.type == VisitOrderType.PVO }
      .minByOrNull { it.createdTimestamp }
      ?: dpsPrisonerDetails.visitOrders
        .asSequence()
        .filter { it.status == VisitOrderStatus.AVAILABLE }
        .filter { it.type == VisitOrderType.VO }
        .minByOrNull { it.createdTimestamp }

    if (selected != null) {
      selected.status = VisitOrderStatus.USED
      selected.visitReference = visit.reference
    } else {
      // If none are found, generate a negative VO and save to prisoners negativeVisitOrders list.
      val negativeVo = NegativeVisitOrder(
        status = NegativeVisitOrderStatus.USED,
        type = VisitOrderType.VO,
        prisonerId = dpsPrisonerDetails.prisonerId,
        prisoner = dpsPrisonerDetails,
        visitReference = visit.reference,
      )
      dpsPrisonerDetails.negativeVisitOrders.add(negativeVo)
    }

    dpsPrisonerDetails.changeLogs.add(changeLogService.createLogAllocationUsedByVisit(dpsPrisonerDetails, visit.reference))

    prisonerDetailsService.updatePrisonerDetails(dpsPrisonerDetails)

    telemetryClient.trackEvent(
      "allocation-api-vo-consumed-by-visit",
      mapOf(
        "visitReference" to visit.reference,
        "prisonerId" to visit.prisonerId,
        "voType" to (selected?.type?.name ?: "vo"),
      ),
      null,
    )
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun processPrisonerAllocation(prisonerId: String, jobReference: String, allPrisonIncentiveAmounts: List<PrisonIncentiveAmountsDto>, fromRetryQueue: Boolean? = false): ChangeLog? {
    LOG.info("Entered ProcessPrisonerService - processPrisoner for prisoner - $prisonerId")

    try {
      // Get prisoner on DPS (or create if they're new).
      val dpsPrisonerDetails: PrisonerDetails = prisonerDetailsService.getPrisonerDetails(prisonerId)
        ?: prisonerDetailsService.createPrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)

      val dpsPrisonerDetailsBefore = dpsPrisonerDetails.deepCopy()

      processPrisonerAllocation(dpsPrisonerDetails, allPrisonIncentiveAmounts)
      processPrisonerAccumulation(dpsPrisonerDetails)
      processPrisonerExpiration(dpsPrisonerDetails)

      if (hasChangeOccurred(dpsPrisonerDetailsBefore, dpsPrisonerDetails)) {
        dpsPrisonerDetails.changeLogs.add(changeLogService.createLogBatchProcess(dpsPrisonerDetails))
      }

      val savedPrisoner = prisonerDetailsService.updatePrisonerDetails(dpsPrisonerDetails)

      // Return the inserted change log, which can be used by caller to raise event for prisoner processing.
      return savedPrisoner.changeLogs.firstOrNull { it.changeType == ChangeLogType.BATCH_PROCESS && it.changeTimestamp.toLocalDate() == LocalDate.now() }
    } catch (e: Exception) {
      // When a prisoner is processed from the retry queue, we don't want to add them back if an exception happens.
      // Instead, it should go onto the DLQ.
      if (fromRetryQueue == false) {
        LOG.error("Error processing prisoner - $prisonerId, putting $prisonerId on prisoner retry queue", e)
        prisonerRetryService.sendMessageToPrisonerRetryQueue(
          jobReference = jobReference,
          prisonerId = prisonerId,
        )
      } else {
        LOG.error("Error processing prisoner - $prisonerId from retry queue", e)
        throw e
      }
    }

    return null
  }

  private fun hasChangeOccurred(
    before: PrisonerDetails,
    after: PrisonerDetails,
  ): Boolean {
    val voChanged = before.visitOrders.toSet() != after.visitOrders.toSet()
    val nvoChanged = before.negativeVisitOrders.toSet() != after.negativeVisitOrders.toSet()

    return voChanged || nvoChanged
  }

  private fun processPrisonerAllocation(dpsPrisoner: PrisonerDetails, allPrisonIncentiveAmounts: List<PrisonIncentiveAmountsDto>) {
    LOG.info("Entered ProcessPrisonerService - processPrisonerAllocation with prisonerId ${dpsPrisoner.prisonerId}")

    val prisonerPrisonId = prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId).prisonId
    val prisonerIncentive = incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)

    val prisonIncentiveAmounts = allPrisonIncentiveAmounts.firstOrNull { it.levelCode == prisonerIncentive.iepCode }
      ?: incentivesClient.getPrisonIncentiveLevelByLevelCode(prisonerPrisonId, prisonerIncentive.iepCode)

    val visitOrders = mutableListOf<VisitOrder>()
    visitOrders.addAll(generateVos(dpsPrisoner, prisonIncentiveAmounts))
    visitOrders.addAll(generatePVos(dpsPrisoner, prisonIncentiveAmounts))

    // Capture the VO / PVOs created, to see if we need to update allocation dates.
    updateLastAllocatedDates(dpsPrisoner, visitOrders)

    dpsPrisoner.visitOrders.addAll(visitOrders)

    LOG.info("Successfully generated ${visitOrders.size} visit orders for prisoner ${dpsPrisoner.prisonerId}: " + "${visitOrders.count { it.type == VisitOrderType.PVO }} PVOs and ${visitOrders.count { it.type == VisitOrderType.VO }} VOs")
  }

  private fun processPrisonerAccumulation(dpsPrisoner: PrisonerDetails) {
    LOG.info("Entered ProcessPrisonerService - processPrisonerAccumulation with prisonerId: ${dpsPrisoner.prisonerId}")

    dpsPrisoner.visitOrders.filter { it.type == VisitOrderType.VO && it.status == VisitOrderStatus.AVAILABLE && it.createdTimestamp.isBefore(LocalDateTime.now().minusDays(28)) }.forEach { it.status = VisitOrderStatus.ACCUMULATED }
    LOG.info("Completed accumulation for ${dpsPrisoner.prisonerId}")
  }

  private fun processPrisonerExpiration(dpsPrisoner: PrisonerDetails) {
    LOG.info("Entered ProcessPrisonerService - processPrisonerExpiration with prisonerId: ${dpsPrisoner.prisonerId}")

    // Expire all VOs over the maximum accumulation cap.
    val currentAccumulatedVoCount = dpsPrisoner.visitOrders.count { it.type == VisitOrderType.VO && it.status == VisitOrderStatus.ACCUMULATED }
    LOG.info("prisoner ${dpsPrisoner.prisonerId}, has $currentAccumulatedVoCount accumulated VOs. Checking if it's more than allowed maximum $maxAccumulatedVisitOrders")
    if (currentAccumulatedVoCount > maxAccumulatedVisitOrders) {
      val amountToExpire = currentAccumulatedVoCount - maxAccumulatedVisitOrders
      LOG.info("prisoner ${dpsPrisoner.prisonerId} has $currentAccumulatedVoCount VOs. This is more than maximum allowed accumulated VOs $maxAccumulatedVisitOrders. Expiring $amountToExpire VOs")

      dpsPrisoner.visitOrders
        .filter { it.type == VisitOrderType.VO && it.status == VisitOrderStatus.ACCUMULATED }
        .sortedBy { it.createdTimestamp }
        .take(amountToExpire)
        .forEach {
          it.status = VisitOrderStatus.EXPIRED
          it.expiryDate = LocalDate.now()
        }
    }

    // Expire all PVOs older than 28 days.
    dpsPrisoner.visitOrders
      .filter {
        it.type == VisitOrderType.PVO &&
          it.status == VisitOrderStatus.AVAILABLE &&
          it.createdTimestamp.isBefore(LocalDateTime.now().minusDays(28))
      }
      .forEach {
        it.status = VisitOrderStatus.EXPIRED
        it.expiryDate = LocalDate.now()
      }

    LOG.info("Completed expiry for prisoner ${dpsPrisoner.prisonerId}")
  }

  private fun updateLastAllocatedDates(dpsPrisoner: PrisonerDetails, visitOrders: MutableList<VisitOrder>) {
    // Only update the lastVoAllocatedDate and lastPvoAllocatedDate if VOs and PVOs have been generated.
    if (visitOrders.any { it.type == VisitOrderType.VO }) {
      dpsPrisoner.lastVoAllocatedDate = LocalDate.now()
    }
    if (visitOrders.any { it.type == VisitOrderType.PVO }) {
      dpsPrisoner.lastPvoAllocatedDate = LocalDate.now()
    }
  }

  private fun createVisitOrder(prisoner: PrisonerDetails, type: VisitOrderType): VisitOrder = VisitOrder(
    prisonerId = prisoner.prisonerId,
    type = type,
    status = VisitOrderStatus.AVAILABLE,
    createdTimestamp = LocalDateTime.now(),
    expiryDate = null,
    prisoner = prisoner,
  )

  private fun isDueVO(prisoner: PrisonerDetails): Boolean = prisoner.lastVoAllocatedDate <= LocalDate.now().minusDays(14)

  private fun isDuePVO(prisoner: PrisonerDetails): Boolean {
    val lastPVODate = prisoner.lastPvoAllocatedDate

    // If they haven't been given a PVO before, we wait until their VO due date to allocate it, to align the dates.
    if (lastPVODate == null) {
      return isDueVO(prisoner)
    }

    return lastPVODate <= LocalDate.now().minusDays(28)
  }

  private fun generateVos(prisoner: PrisonerDetails, prisonIncentivesForPrisonerLevel: PrisonIncentiveAmountsDto): List<VisitOrder> {
    val visitOrders = mutableListOf<VisitOrder>()
    if (isDueVO(prisoner)) {
      val negativeVoCount = prisoner.negativeVisitOrders.count { it.type == VisitOrderType.VO && it.status == NegativeVisitOrderStatus.USED }
      if (negativeVoCount > 0) {
        handleNegativeBalanceRepayment(prisonIncentivesForPrisonerLevel.visitOrders, negativeVoCount, prisoner, VisitOrderType.VO, visitOrders)
      } else {
        repeat(prisonIncentivesForPrisonerLevel.visitOrders) {
          visitOrders.add(createVisitOrder(prisoner, VisitOrderType.VO))
        }
      }
    }
    return visitOrders
  }

  private fun generatePVos(prisoner: PrisonerDetails, prisonIncentivesForPrisonerLevel: PrisonIncentiveAmountsDto): List<VisitOrder> {
    val visitOrders = mutableListOf<VisitOrder>()

    if (prisonIncentivesForPrisonerLevel.privilegedVisitOrders != 0 && isDuePVO(prisoner)) {
      val negativePvoCount = prisoner.negativeVisitOrders.count { it.type == VisitOrderType.PVO && it.status == NegativeVisitOrderStatus.USED }
      if (negativePvoCount > 0) {
        handleNegativeBalanceRepayment(prisonIncentivesForPrisonerLevel.privilegedVisitOrders, negativePvoCount, prisoner, VisitOrderType.PVO, visitOrders)
      } else {
        repeat(prisonIncentivesForPrisonerLevel.privilegedVisitOrders) {
          visitOrders.add(createVisitOrder(prisoner, VisitOrderType.PVO))
        }
      }
    }
    return visitOrders
  }

  private fun handleNegativeBalanceRepayment(incentiveAmount: Int, negativeBalance: Int, prisoner: PrisonerDetails, type: VisitOrderType, visitOrders: MutableList<VisitOrder>) {
    if (incentiveAmount < negativeBalance) {
      // If the incentive amount doesn't fully cover debt, then only repay what is possible.
      prisoner.negativeVisitOrders
        .filter { it.type == type && it.status == NegativeVisitOrderStatus.USED }
        .sortedBy { it.createdTimestamp }
        .take(incentiveAmount)
        .forEach {
          it.status = NegativeVisitOrderStatus.REPAID
          it.repaidDate = LocalDate.now()
        }
    } else {
      // If the incentive amount pushes the balance positive, repay all debt and generate the required amount of positive VO / PVOs.
      prisoner.negativeVisitOrders
        .filter { it.type == type && it.status == NegativeVisitOrderStatus.USED }
        .forEach {
          it.status = NegativeVisitOrderStatus.REPAID
          it.repaidDate = LocalDate.now()
        }

      val visitOrdersToCreate = incentiveAmount - negativeBalance

      repeat(visitOrdersToCreate) {
        visitOrders.add(createVisitOrder(prisoner, type))
      }
    }
  }
}
