package uk.gov.justice.digital.hmpps.visitallocationapi.service

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
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationPrisonJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import java.time.LocalDate
import java.time.LocalDateTime

@Transactional
@Service
class AllocationService(
  private val prisonerSearchClient: PrisonerSearchClient,
  private val incentivesClient: IncentivesClient,
  private val visitOrderRepository: VisitOrderRepository,
  private val visitOrderAllocationPrisonJobRepository: VisitOrderAllocationPrisonJobRepository,
  private val prisonerDetailsService: PrisonerDetailsService,
  private val prisonerRetryService: PrisonerRetryService,
  private val negativeVisitOrderRepository: NegativeVisitOrderRepository,
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
        processPrisonerAllocation(prisoner.prisonerId, prisoner, allIncentiveLevels)
        processPrisonerAccumulation(prisoner.prisonerId)
        processPrisonerExpiration(prisoner.prisonerId)
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

  suspend fun processPrisonerAllocation(prisonerId: String, prisonerDto: PrisonerDto? = null, allPrisonIncentiveAmounts: List<PrisonIncentiveAmountsDto>? = null) {
    LOG.info("Entered AllocationService - processPrisonerAllocation with prisonerId $prisonerId")

    val prisoner = prisonerDto ?: prisonerSearchClient.getPrisonerById(prisonerId)
    val prisonerIncentive = incentivesClient.getPrisonerIncentiveReviewHistory(prisoner.prisonerId)
    val prisonIncentiveAmounts = allPrisonIncentiveAmounts?.firstOrNull { it.levelCode == prisonerIncentive.iepCode }
      ?: incentivesClient.getPrisonIncentiveLevelByLevelCode(prisoner.prisonId, prisonerIncentive.iepCode)

    val visitOrders = mutableListOf<VisitOrder>()

    // add VOs
    visitOrders.addAll(generateVos(prisoner, prisonIncentiveAmounts))

    // add PVOs
    visitOrders.addAll(generatePVos(prisoner, prisonIncentiveAmounts))

    visitOrderRepository.saveAll(visitOrders)

    updateLastAllocatedDates(prisoner, visitOrders)

    LOG.info(
      "Successfully generated ${visitOrders.size} visit orders for prisoner prisoner $prisonerId: " + "${visitOrders.count { it.type == VisitOrderType.PVO }} PVOs and ${visitOrders.count { it.type == VisitOrderType.VO }} VOs",
    )
  }

  private fun updateLastAllocatedDates(prisoner: PrisonerDto, visitOrders: MutableList<VisitOrder>) {
    // Only update the lastVoAllocatedDate and lastPvoAllocatedDate if VOs and PVOs have been generated.
    if (visitOrders.any { it.type == VisitOrderType.VO }) {
      prisonerDetailsService.updateVoLastCreatedDateOrCreatePrisoner(prisonerId = prisoner.prisonerId, LocalDate.now())
    }
    if (visitOrders.any { it.type == VisitOrderType.PVO }) {
      prisonerDetailsService.updatePvoLastCreatedDate(prisonerId = prisoner.prisonerId, LocalDate.now())
    }
  }

  private fun processPrisonerAccumulation(prisonerId: String) {
    LOG.info("Entered AllocationService - processPrisonerAccumulation with prisonerId: $prisonerId")

    val vosAccumulated = visitOrderRepository.updateAvailableVisitOrdersOver28DaysToAccumulated(prisonerId, VisitOrderType.VO)
    LOG.info("Accumulated $vosAccumulated VOs for prisoner $prisonerId")
  }

  private fun processPrisonerExpiration(prisonerId: String) {
    LOG.info("Entered AllocationService - processPrisonerExpiration with prisonerId: $prisonerId")

    // Expire all VOs over the maximum accumulation cap.
    val currentAccumulatedVoCount = visitOrderRepository.countAllVisitOrders(prisonerId, VisitOrderType.VO, VisitOrderStatus.ACCUMULATED)
    if (currentAccumulatedVoCount > maxAccumulatedVisitOrders) {
      val amountToExpire = currentAccumulatedVoCount - maxAccumulatedVisitOrders
      LOG.info("prisoner $prisonerId has $currentAccumulatedVoCount VOs. This is more than maximum allowed accumulated VOs $maxAccumulatedVisitOrders. Expiring $amountToExpire VOs")
      val vosExpired = visitOrderRepository.expireOldestAccumulatedVisitOrders(prisonerId, amountToExpire.toLong())
      LOG.info("Expired $vosExpired VOs for prisoner $prisonerId")
    }

    // Expire all PVOs older than 28 days.
    val pvosExpired = visitOrderRepository.expirePrivilegedVisitOrdersOver28Days(prisonerId)
    LOG.info("Expired $pvosExpired PVOs for prisoner $prisonerId")
  }

  private fun createVisitOrder(prisonerId: String, type: VisitOrderType): VisitOrder = VisitOrder(
    prisonerId = prisonerId,
    type = type,
    status = VisitOrderStatus.AVAILABLE,
    createdTimestamp = LocalDateTime.now(),
    expiryDate = null,
  )

  private fun isDueVO(prisonerId: String): Boolean {
    val lastVODate = prisonerDetailsService.getPrisoner(prisonerId)?.lastVoAllocatedDate
    return lastVODate == null || lastVODate <= LocalDate.now().minusDays(14)
  }

  private fun isDuePVO(prisonerId: String): Boolean {
    val lastPVODate = prisonerDetailsService.getPrisoner(prisonerId)?.lastPvoAllocatedDate

    // If they haven't been given a PVO before, we wait until their VO due date to allocate it, to align the dates.
    if (lastPVODate == null) {
      return isDueVO(prisonerId)
    }

    return lastPVODate <= LocalDate.now().minusDays(28)
  }

  private fun generateVos(prisoner: PrisonerDto, prisonIncentivesForPrisonerLevel: PrisonIncentiveAmountsDto): List<VisitOrder> {
    val visitOrders = mutableListOf<VisitOrder>()
    if (isDueVO(prisoner.prisonerId)) {
      val negativeVoCount = negativeVisitOrderRepository.countAllNegativeVisitOrders(prisoner.prisonerId, VisitOrderType.VO, NegativeVisitOrderStatus.USED)
      if (negativeVoCount > 0) {
        handleNegativeBalanceRepayment(prisonIncentivesForPrisonerLevel.visitOrders, negativeVoCount, prisoner, VisitOrderType.VO, visitOrders)
      } else {
        repeat(prisonIncentivesForPrisonerLevel.visitOrders) {
          visitOrders.add(createVisitOrder(prisoner.prisonerId, VisitOrderType.VO))
        }
      }
    }
    return visitOrders.toList()
  }

  private fun generatePVos(prisoner: PrisonerDto, prisonIncentivesForPrisonerLevel: PrisonIncentiveAmountsDto): List<VisitOrder> {
    val visitOrders = mutableListOf<VisitOrder>()

    if (prisonIncentivesForPrisonerLevel.privilegedVisitOrders != 0 && isDuePVO(prisoner.prisonerId)) {
      val negativePvoCount = negativeVisitOrderRepository.countAllNegativeVisitOrders(prisoner.prisonerId, VisitOrderType.PVO, NegativeVisitOrderStatus.USED)
      if (negativePvoCount > 0) {
        handleNegativeBalanceRepayment(prisonIncentivesForPrisonerLevel.privilegedVisitOrders, negativePvoCount, prisoner, VisitOrderType.PVO, visitOrders)
      } else {
        repeat(prisonIncentivesForPrisonerLevel.privilegedVisitOrders) {
          visitOrders.add(createVisitOrder(prisoner.prisonerId, VisitOrderType.PVO))
        }
      }
    }
    return visitOrders.toList()
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

  private fun handleNegativeBalanceRepayment(incentiveAmount: Int, negativeBalance: Int, prisoner: PrisonerDto, type: VisitOrderType, visitOrders: MutableList<VisitOrder>) {
    if (incentiveAmount < negativeBalance) {
      negativeVisitOrderRepository.repayNegativeVisitOrdersGivenAmount(
        prisoner.prisonerId,
        type,
        incentiveAmount.toLong(),
      )
    } else {
      negativeVisitOrderRepository.repayNegativeVisitOrdersGivenAmount(
        prisoner.prisonerId,
        type,
        null,
      )

      val visitOrdersToCreate = incentiveAmount - negativeBalance

      repeat(visitOrdersToCreate) {
        visitOrders.add(createVisitOrder(prisoner.prisonerId, type))
      }
    }
  }
}
