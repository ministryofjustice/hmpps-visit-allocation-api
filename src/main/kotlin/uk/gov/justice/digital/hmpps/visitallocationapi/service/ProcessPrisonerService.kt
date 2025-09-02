package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.IncentivesClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.snapshots.snapshot
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.visit.scheduler.VisitDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.PrisonerChangeTrackingUtil
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.*

@Transactional
@Service
class ProcessPrisonerService(
  private val prisonerSearchClient: PrisonerSearchClient,
  private val incentivesClient: IncentivesClient,
  private val prisonerDetailsService: PrisonerDetailsService,
  private val prisonerRetryService: PrisonerRetryService,
  private val changeLogService: ChangeLogService,
  private val telemetryClientService: TelemetryClientService,
  @Value("\${max.visit-orders:26}") val maxAccumulatedVisitOrders: Int,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun processPrisonerVisitOrderUsage(visit: VisitDto): UUID? {
    val dpsPrisonerDetails = prisonerDetailsService.getPrisonerDetails(visit.prisonerId)
      ?: prisonerDetailsService.createPrisonerDetails(visit.prisonerId, LocalDate.now().minusDays(14), null)

    // Due to our SQS queues being "At least once delivery", this specific event needs to return early if this visit has already been mapped.
    if (visitAlreadyMapped(dpsPrisonerDetails, visit)) {
      LOG.info("Duplicate request to map a visit booking (${visit.reference}) to a visit order for prisoner ${dpsPrisonerDetails.prisonerId}. Exiting early.")
      return null
    }

    // Find the oldest PVO to use. If none exists, find the oldest VO to use.
    val selected: VisitOrder? = dpsPrisonerDetails.visitOrders
      .asSequence()
      .filter { it.type == VisitOrderType.PVO }
      .filter { it.status == VisitOrderStatus.AVAILABLE }
      .minByOrNull { it.createdTimestamp }
      ?: dpsPrisonerDetails.visitOrders
        .asSequence()
        .filter { it.type == VisitOrderType.VO }
        .filter { it.status in listOf(VisitOrderStatus.AVAILABLE, VisitOrderStatus.ACCUMULATED) }
        .minByOrNull { it.createdTimestamp }

    if (selected != null) {
      selected.status = VisitOrderStatus.USED
      selected.visitReference = visit.reference
    } else {
      // If none are found, generate a negative VO and save to prisoners negativeVisitOrders list.
      val negativeVo = NegativeVisitOrder(
        status = NegativeVisitOrderStatus.USED,
        type = VisitOrderType.VO,
        prisoner = dpsPrisonerDetails,
        visitReference = visit.reference,
      )
      dpsPrisonerDetails.negativeVisitOrders.add(negativeVo)
    }

    val changeLog = changeLogService.createLogAllocationUsedByVisit(dpsPrisonerDetails, visit.reference)
    dpsPrisonerDetails.changeLogs.add(changeLog)

    telemetryClientService.trackEvent(
      TelemetryEventType.VO_CONSUMED_BY_VISIT,
      mapOf(
        "visitReference" to visit.reference,
        "prisonerId" to visit.prisonerId,
        "voType" to (selected?.type?.name ?: "vo"),
      ),
    )

    return changeLog.reference
  }

  fun processPrisonerVisitOrderRefund(visit: VisitDto): UUID? {
    val dpsPrisonerDetails = prisonerDetailsService.getPrisonerDetails(visit.prisonerId)
      ?: prisonerDetailsService.createPrisonerDetails(visit.prisonerId, LocalDate.now().minusDays(14), null)

    // Find the VO used by the visit.
    val voUsedForVisit = dpsPrisonerDetails.visitOrders.firstOrNull { it.visitReference == visit.reference }

    if (voUsedForVisit != null) {
      if (voUsedForVisit.type == VisitOrderType.VO && hasPrisonerReachedVoCap(dpsPrisonerDetails)) {
        LOG.info("Prisoner ${dpsPrisonerDetails.prisonerId} already has the maximum number of VOs. Refund for visit ${visit.reference} will not be processed.")
        return null
      }

      voUsedForVisit.status = VisitOrderStatus.AVAILABLE
      voUsedForVisit.visitReference = null

      // If it's a PVO, we also set the created date to the 1st of the month, to avoid instant expiry scenarios.
      if (voUsedForVisit.type == VisitOrderType.PVO) {
        voUsedForVisit.createdTimestamp = LocalDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).with(LocalTime.MIN)
      }
    } else {
      // If none are found, find the negative VO used for the visit, and remove it, as it was never used.
      val negativeVoUsedForVisit = dpsPrisonerDetails.negativeVisitOrders.firstOrNull { it.visitReference == visit.reference }
      if (negativeVoUsedForVisit != null) {
        dpsPrisonerDetails.negativeVisitOrders.remove(negativeVoUsedForVisit)
      } else {
        if (hasPrisonerReachedVoCap(dpsPrisonerDetails)) {
          LOG.info("Prisoner ${dpsPrisonerDetails.prisonerId} already has the maximum number of VOs. Refund for visit ${visit.reference} will not be processed.")
          return null
        }

        LOG.warn("No visit with reference ${visit.reference} associated with prisoner ${visit.prisonerId} found on visit allocation api. Creating VO.")
        dpsPrisonerDetails.visitOrders.add(createVisitOrder(dpsPrisonerDetails, VisitOrderType.VO))
      }
    }

    val changeLog = changeLogService.createLogAllocationRefundedByVisitCancelled(dpsPrisonerDetails, visit.reference)
    dpsPrisonerDetails.changeLogs.add(changeLog)

    telemetryClientService.trackEvent(
      TelemetryEventType.VO_REFUNDED_AFTER_VISIT_CANCELLATION,
      mapOf(
        "visitReference" to visit.reference,
        "prisonerId" to visit.prisonerId,
      ),
    )

    return changeLog.reference
  }

  fun processPrisonerMerge(newPrisonerId: String, removedPrisonerId: String): UUID? {
    var visitOrdersToBeCreated = 0
    var privilegedVisitOrdersToBeCreated = 0

    LOG.info("processPrisonerMerge with newPrisonerId - $newPrisonerId and removedPrisonerId - $removedPrisonerId")
    val newPrisonerDetails = prisonerDetailsService.getPrisonerDetails(newPrisonerId)
      ?: prisonerDetailsService.createPrisonerDetails(newPrisonerId, LocalDate.now().minusDays(14), null)

    val removedPrisonerDetails = prisonerDetailsService.getPrisonerDetails(removedPrisonerId)

    if (removedPrisonerDetails != null) {
      // create VOs - if the VO balance of the new prisoner is less than the removed prisoner's VO balance
      visitOrdersToBeCreated = removedPrisonerDetails.getVoBalance() - newPrisonerDetails.getVoBalance()
      if (visitOrdersToBeCreated > 0) {
        LOG.info("Creating $visitOrdersToBeCreated new VOs for prisoner - $newPrisonerId post merge with removed prisoner - $removedPrisonerId")
        repeat(visitOrdersToBeCreated) {
          val lastVoAllocatedDate = newPrisonerDetails.lastVoAllocatedDate
          newPrisonerDetails.visitOrders.add(createVisitOrder(newPrisonerDetails, VisitOrderType.VO, createdTimestamp = lastVoAllocatedDate.atStartOfDay()))
        }
      }

      // create PVOs - if the PVO balance of the new prisoner is less than the removed prisoner's PVO balance
      privilegedVisitOrdersToBeCreated = removedPrisonerDetails.getPvoBalance() - newPrisonerDetails.getPvoBalance()
      if (privilegedVisitOrdersToBeCreated > 0) {
        LOG.info("Creating $privilegedVisitOrdersToBeCreated new PVOs for prisoner - $newPrisonerId post merge with removed prisoner - $removedPrisonerId")
        repeat(privilegedVisitOrdersToBeCreated) {
          val createdTimestamp = newPrisonerDetails.lastPvoAllocatedDate?.atStartOfDay() ?: LocalDateTime.now()
          newPrisonerDetails.visitOrders.add(createVisitOrder(newPrisonerDetails, VisitOrderType.PVO, createdTimestamp = createdTimestamp))
        }
      }
    } else {
      LOG.info("Prisoner ID - $removedPrisonerId, removed as part of the merge does not exist on VO Allocation DB, no processing needed.")
    }

    return if (visitOrdersToBeCreated > 0 || privilegedVisitOrdersToBeCreated > 0) {
      // add a changelog entry if new VO / PVOs have been added
      val changeLog = changeLogService.createLogAllocationForPrisonerMerge(
        dpsPrisoner = newPrisonerDetails,
        newPrisonerId = newPrisonerId,
        removedPrisonerId = removedPrisonerId,
      )
      newPrisonerDetails.changeLogs.add(changeLog)

      telemetryClientService.trackEvent(
        TelemetryEventType.VO_ADDED_POST_MERGE,
        mapOf(
          "prisonerId" to newPrisonerId,
          "removedPrisonerId" to removedPrisonerId,
          "voAddedPostMerge" to visitOrdersToBeCreated.toString(),
          "pvoAddedPostMerge" to privilegedVisitOrdersToBeCreated.toString(),
        ),
      )
      changeLog.reference
    } else {
      LOG.info("No VOs / PVOs were added post merge of prisonerId - $newPrisonerId and removedPrisonerId - $removedPrisonerId")
      null
    }
  }

  fun processPrisonerReceivedResetBalance(prisonerId: String, reason: PrisonerReceivedReasonType): UUID {
    val dpsPrisonerDetails = prisonerDetailsService.getPrisonerDetails(prisonerId)
      ?: prisonerDetailsService.createPrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)

    dpsPrisonerDetails.visitOrders
      .filter { it.status in listOf(VisitOrderStatus.AVAILABLE, VisitOrderStatus.ACCUMULATED) }
      .forEach {
        it.status = VisitOrderStatus.EXPIRED
        it.expiryDate = LocalDate.now()
      }

    dpsPrisonerDetails.negativeVisitOrders
      .filter { it.status == NegativeVisitOrderStatus.USED }
      .forEach {
        it.status = NegativeVisitOrderStatus.REPAID
        it.repaidDate = LocalDate.now()
      }

    val changeLog = changeLogService.createLogPrisonerBalanceReset(dpsPrisonerDetails, reason)
    dpsPrisonerDetails.changeLogs.add(changeLog)

    telemetryClientService.trackEvent(
      TelemetryEventType.VO_PRISONER_BALANCE_RESET,
      mapOf(
        "prisonerId" to prisonerId,
        "reason" to reason.name,
      ),
    )

    return changeLog.reference
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun processPrisonerAllocation(prisonerId: String, jobReference: String, allPrisonIncentiveAmounts: List<PrisonIncentiveAmountsDto>, fromRetryQueue: Boolean? = false): UUID? {
    LOG.info("Entered ProcessPrisonerService - processPrisoner for prisoner - $prisonerId")

    try {
      // Get prisoner on DPS (or create if they're new).
      val dpsPrisonerDetails: PrisonerDetails = prisonerDetailsService.getPrisonerDetails(prisonerId)
        ?: prisonerDetailsService.createPrisonerDetails(prisonerId, LocalDate.now().minusDays(14), null)

      // Capture the before details, used at the end to track if changes have been made. If so, a change_log entry will be generated.
      val dpsPrisonerDetailsBefore = dpsPrisonerDetails.snapshot()

      processPrisonerAccumulation(dpsPrisonerDetails)
      processPrisonerAllocation(dpsPrisonerDetails, allPrisonIncentiveAmounts)
      processPrisonerExpiration(dpsPrisonerDetails)

      val changeLog: ChangeLog? = if (PrisonerChangeTrackingUtil.hasChangeOccurred(dpsPrisonerDetailsBefore, dpsPrisonerDetails)) {
        changeLogService.createLogBatchProcess(dpsPrisonerDetails).also {
          dpsPrisonerDetails.changeLogs.add(it)
        }
      } else {
        null
      }

      return changeLog?.reference
    } catch (e: Exception) {
      // When a prisoner is processed from the retry queue, we don't want to add them back if an exception happens.
      // Instead, it should go onto the DLQ.
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()

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

  private fun hasPrisonerReachedVoCap(dpsPrisonerDetails: PrisonerDetails): Boolean = dpsPrisonerDetails.visitOrders.count { it.type == VisitOrderType.VO && it.status in listOf(VisitOrderStatus.AVAILABLE, VisitOrderStatus.ACCUMULATED) } >= maxAccumulatedVisitOrders

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

  private fun createVisitOrder(
    prisoner: PrisonerDetails,
    type: VisitOrderType,
    createdTimestamp: LocalDateTime = LocalDateTime.now(),
  ): VisitOrder = VisitOrder(
    type = type,
    status = VisitOrderStatus.AVAILABLE,
    createdTimestamp = createdTimestamp,
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
        repeat(amountOfVosToGenerate(prisoner, prisonIncentivesForPrisonerLevel.visitOrders)) {
          visitOrders.add(createVisitOrder(prisoner, VisitOrderType.VO))
        }
      }
    }
    return visitOrders
  }

  private fun amountOfVosToGenerate(prisoner: PrisonerDetails, incentiveLevelAllocation: Int): Int {
    val currentVOs = prisoner.visitOrders.count {
      it.type == VisitOrderType.VO && (it.status == VisitOrderStatus.AVAILABLE || it.status == VisitOrderStatus.ACCUMULATED)
    }

    // Don't go past 0 as a safeguard
    val remainingVoAllowance = (maxAccumulatedVisitOrders - currentVOs).coerceAtLeast(0)

    // Return the maximum they can be allocated without breaching the hard cap maxAccumulatedVisitOrders
    return incentiveLevelAllocation.coerceAtMost(remainingVoAllowance)
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

  private fun visitAlreadyMapped(dpsPrisonerDetails: PrisonerDetails, visit: VisitDto): Boolean = dpsPrisonerDetails.visitOrders.any { it.visitReference == visit.reference } || dpsPrisonerDetails.negativeVisitOrders.any { it.visitReference == visit.reference }
}
