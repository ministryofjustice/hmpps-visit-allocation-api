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
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
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
    val prisonIncentiveAmounts = allPrisonIncentiveAmounts?.first { it.levelCode == prisonerIncentive.iepCode }
      ?: incentivesClient.getPrisonIncentiveLevelByLevelCode(prisoner.prisonId, prisonerIncentive.iepCode)

    val visitOrders = mutableListOf<VisitOrder>()

    // add VOs
    visitOrders.addAll(generateVos(prisoner, prisonIncentiveAmounts))

    // add PVOs
    visitOrders.addAll(generatePVos(prisoner, prisonIncentiveAmounts))

    visitOrderRepository.saveAll(visitOrders)

    LOG.info(
      "Successfully generated ${visitOrders.size} visit orders for prisoner prisoner $prisonerId: " + "${visitOrders.count { it.type == VisitOrderType.PVO }} PVOs and ${visitOrders.count { it.type == VisitOrderType.VO }} VOs",
    )
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
      val vosExpired = visitOrderRepository.expireOldestAccumulatedVisitOrders(prisonerId, amountToExpire)
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
    createdDate = LocalDate.now(),
    expiryDate = null,
  )

  private fun isDueVO(prisonerId: String): Boolean {
    val lastVODate = visitOrderRepository.findLastAllocatedDate(prisonerId, VisitOrderType.VO)
    return lastVODate == null || lastVODate <= LocalDate.now().minusDays(14)
  }

  private fun isDuePVO(prisonerId: String): Boolean {
    val lastPVODate = visitOrderRepository.findLastAllocatedDate(prisonerId, VisitOrderType.PVO)

    // If they haven't been given a PVO before, we wait until their VO due date to allocate it.
    if (lastPVODate == null) {
      return isDueVO(prisonerId)
    }

    return lastPVODate <= LocalDate.now().minusDays(28)
  }

  private fun generateVos(prisoner: PrisonerDto, prisonIncentivesForPrisonerLevel: PrisonIncentiveAmountsDto): List<VisitOrder> {
    // Generate VOs
    val visitOrders = mutableListOf<VisitOrder>()
    if (isDueVO(prisoner.prisonerId)) {
      repeat(prisonIncentivesForPrisonerLevel.visitOrders) {
        visitOrders.add(createVisitOrder(prisoner.prisonerId, VisitOrderType.VO))
      }
    }

    return visitOrders.toList()
  }

  private fun generatePVos(prisoner: PrisonerDto, prisonIncentivesForPrisonerLevel: PrisonIncentiveAmountsDto): List<VisitOrder> {
    // Generate PVOs
    val visitOrders = mutableListOf<VisitOrder>()

    if (prisonIncentivesForPrisonerLevel.privilegedVisitOrders != 0) {
      if (isDuePVO(prisoner.prisonerId)) {
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
}
