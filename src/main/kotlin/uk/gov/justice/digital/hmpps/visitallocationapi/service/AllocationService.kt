package uk.gov.justice.digital.hmpps.visitallocationapi.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.IncentivesClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationPrisonJobRepository
import java.time.LocalDate
import java.time.LocalDateTime

@Transactional
@Service
class AllocationService(
  private val prisonerSearchClient: PrisonerSearchClient,
  private val incentivesClient: IncentivesClient,
  private val visitOrderAllocationPrisonJobRepository: VisitOrderAllocationPrisonJobRepository,
  private val prisonerDetailsService: PrisonerDetailsService,
  private val prisonerRetryService: PrisonerRetryService,
  @Value("\${max.visit-orders:26}") val maxAccumulatedVisitOrders: Int,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun processPrison(jobReference: String, prisonId: String) {
    LOG.info("Entered AllocationService - processPrisonAllocation with job reference - $jobReference , prisonCode - $prisonId")
    setVisitOrderAllocationPrisonJobStartTime(jobReference, prisonId)

    val allPrisoners = getConvictedPrisonersForPrison(jobReference = jobReference, prisonId = prisonId)
    val allIncentiveLevels = getIncentiveLevelsForPrison(jobReference = jobReference, prisonId = prisonId)
    var totalConvictedPrisonersProcessed = 0
    var totalConvictedPrisonersFailed = 0

    for (prisoner in allPrisoners) {
      try {
        // Get prisoner on DPS (or create if they're new).
        val dpsPrisonerDetails: PrisonerDetails = (withContext(Dispatchers.IO) { prisonerDetailsService.getPrisoner(prisoner.prisonerId) } ?: withContext(Dispatchers.IO) { prisonerDetailsService.createNewPrisonerDetails(prisoner.prisonerId, LocalDate.now().minusDays(14), null) })

        processPrisonerAllocation(dpsPrisonerDetails, allIncentiveLevels)
        processPrisonerAccumulation(dpsPrisonerDetails)
        processPrisonerExpiration(dpsPrisonerDetails)

        withContext(Dispatchers.IO) {
          prisonerDetailsService.updatePrisonerDetails(dpsPrisonerDetails)
        }

        totalConvictedPrisonersProcessed++
      } catch (e: Exception) {
        // ignore the prisoner and send it to an SQS queue to ensure the whole process does not stop
        LOG.error("Error processing prisoner - ${prisoner.prisonerId}, putting ${prisoner.prisonerId} on prisoner retry queue", e)
        totalConvictedPrisonersFailed++
        prisonerRetryService.sendMessageToPrisonerRetryQueue(jobReference = jobReference, prisonerId = prisoner.prisonerId)
      }
    }

    setVisitOrderAllocationPrisonJobEndTimeAndStats(
      jobReference = jobReference,
      prisonCode = prisonId,
      totalConvictedPrisoners = allPrisoners.size,
      totalPrisonersProcessed = totalConvictedPrisonersProcessed,
      totalPrisonersFailed = totalConvictedPrisonersFailed,
    )

    LOG.info("Finished AllocationService - processPrisonAllocation with prisonCode: $prisonId, total records processed : ${allPrisoners.size}")
  }

  suspend fun processPrisonerAllocation(dpsPrisoner: PrisonerDetails, allPrisonIncentiveAmounts: List<PrisonIncentiveAmountsDto>? = null) {
    LOG.info("Entered AllocationService - processPrisonerAllocation with prisonerId ${dpsPrisoner.prisonerId}")

    val prisonerPrisonId = prisonerSearchClient.getPrisonerById(dpsPrisoner.prisonerId).prisonId
    val prisonerIncentive = incentivesClient.getPrisonerIncentiveReviewHistory(dpsPrisoner.prisonerId)

    val prisonIncentiveAmounts = allPrisonIncentiveAmounts?.firstOrNull { it.levelCode == prisonerIncentive.iepCode }
      ?: incentivesClient.getPrisonIncentiveLevelByLevelCode(prisonerPrisonId, prisonerIncentive.iepCode)

    val visitOrders = mutableListOf<VisitOrder>()
    visitOrders.addAll(generateVos(dpsPrisoner, prisonIncentiveAmounts))
    visitOrders.addAll(generatePVos(dpsPrisoner, prisonIncentiveAmounts))

    // Capture the VO / PVOs created, to see if we need to update allocation dates.
    updateLastAllocatedDates(dpsPrisoner, visitOrders)

    dpsPrisoner.visitOrders.addAll(visitOrders)

    LOG.info("Successfully generated ${visitOrders.size} visit orders for prisoner ${dpsPrisoner.prisonerId}: " + "${visitOrders.count { it.type == VisitOrderType.PVO }} PVOs and ${visitOrders.count { it.type == VisitOrderType.VO }} VOs")
  }

  private fun updateLastAllocatedDates(prisoner: PrisonerDetails, visitOrders: MutableList<VisitOrder>) {
    // Only update the lastVoAllocatedDate and lastPvoAllocatedDate if VOs and PVOs have been generated.
    if (visitOrders.any { it.type == VisitOrderType.VO }) {
      prisonerDetailsService.updateVoLastCreatedDate(prisonerId = prisoner.prisonerId, LocalDate.now())
    }
    if (visitOrders.any { it.type == VisitOrderType.PVO }) {
      prisonerDetailsService.updatePvoLastCreatedDate(prisonerId = prisoner.prisonerId, LocalDate.now())
    }
  }

  private fun processPrisonerAccumulation(dpsPrisoner: PrisonerDetails) {
    LOG.info("Entered AllocationService - processPrisonerAccumulation with prisonerId: ${dpsPrisoner.prisonerId}")

    dpsPrisoner.visitOrders.filter { it.type == VisitOrderType.VO && it.status == VisitOrderStatus.AVAILABLE && it.createdTimestamp.isBefore(LocalDateTime.now().minusDays(28)) }.forEach { it.status = VisitOrderStatus.ACCUMULATED }
    LOG.info("Completed accumulation for ${dpsPrisoner.prisonerId}")
  }

  private fun processPrisonerExpiration(dpsPrisoner: PrisonerDetails) {
    LOG.info("Entered AllocationService - processPrisonerExpiration with prisonerId: ${dpsPrisoner.prisonerId}")

    // Expire all VOs over the maximum accumulation cap.
    val currentAccumulatedVoCount = dpsPrisoner.visitOrders.count { it.type == VisitOrderType.VO && it.status == VisitOrderStatus.ACCUMULATED }
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

  private fun setVisitOrderAllocationPrisonJobStartTime(jobReference: String, prisonCode: String) {
    visitOrderAllocationPrisonJobRepository.updateStartTimestamp(jobReference, prisonCode, LocalDateTime.now())
  }

  private fun setVisitOrderAllocationPrisonJobEndTimeAndFailureMessage(jobReference: String, prisonCode: String, failureMessage: String) {
    visitOrderAllocationPrisonJobRepository.updateFailureMessageAndEndTimestamp(allocationJobReference = jobReference, prisonCode = prisonCode, failureMessage, LocalDateTime.now())
  }

  private fun setVisitOrderAllocationPrisonJobEndTimeAndStats(jobReference: String, prisonCode: String, totalConvictedPrisoners: Int, totalPrisonersProcessed: Int, totalPrisonersFailed: Int) {
    visitOrderAllocationPrisonJobRepository.updateEndTimestampAndStats(allocationJobReference = jobReference, prisonCode = prisonCode, LocalDateTime.now(), totalPrisoners = totalConvictedPrisoners, processedPrisoners = totalPrisonersProcessed, failedPrisoners = totalPrisonersFailed)
  }

  private fun getConvictedPrisonersForPrison(jobReference: String, prisonId: String): List<PrisonerDto> {
    val convictedPrisonersForPrison = try {
      prisonerSearchClient.getConvictedPrisonersByPrisonId(prisonId).content.toList()
    } catch (e: Exception) {
      val failureMessage = "failed to get convicted prisoners by prisonId - $prisonId"
      LOG.error(failureMessage, e)
      setVisitOrderAllocationPrisonJobEndTimeAndFailureMessage(jobReference, prisonId, failureMessage)
      throw e
    }

    return convictedPrisonersForPrison
  }

  private fun getIncentiveLevelsForPrison(jobReference: String, prisonId: String): List<PrisonIncentiveAmountsDto> {
    val incentiveLevelsForPrison = try {
      incentivesClient.getPrisonIncentiveLevels(prisonId)
    } catch (e: Exception) {
      val failureMessage = "failed to get incentive levels by prisonId - $prisonId"
      LOG.error(failureMessage, e)
      setVisitOrderAllocationPrisonJobEndTimeAndFailureMessage(jobReference, prisonId, failureMessage)
      throw e
    }

    return incentiveLevelsForPrison
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
