package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.IncentivesClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.AttributeSearchPrisonerDto
import java.util.UUID

@Service
class PrisonAllocationService(
  private val prisonerSearchClient: PrisonerSearchClient,
  private val incentivesClient: IncentivesClient,
  private val visitOrderAllocationJobService: VisitOrderAllocationJobService,
  private val prisonerAllocationService: PrisonerAllocationService,
  private val prisonerRetryService: PrisonerRetryService,
  private val snsService: SnsService,
  private val changeLogService: ChangeLogService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
  fun processPrison(jobReference: String, prisonId: String) {
    LOG.info("Entered AllocationService - processPrisonAllocation with job reference - $jobReference , prisonCode - $prisonId")
    visitOrderAllocationJobService.setVisitOrderAllocationPrisonJobStartTime(jobReference, prisonId)

    val allPrisoners = getConvictedPrisonersForPrison(jobReference = jobReference, prisonId = prisonId)
    val allIncentiveLevels = getIncentiveLevelsForPrison(jobReference = jobReference, prisonId = prisonId)
    var totalConvictedPrisonersProcessed = 0
    var totalConvictedPrisonersFailedOrSkipped = 0

    for (prisoner in allPrisoners) {
      val changeLogReference = processPrisoner(
        prisoner = prisoner,
        jobReference = jobReference,
        allPrisonIncentiveAmounts = allIncentiveLevels,
      )
      if (changeLogReference != null) {
        val changeLog = changeLogService.findChangeLogForPrisonerByReference(prisoner.prisonerId, changeLogReference)
        if (changeLog != null) {
          totalConvictedPrisonersProcessed++
          snsService.sendPrisonAllocationAdjustmentCreatedEvent(changeLog)
        } else {
          totalConvictedPrisonersFailedOrSkipped++
        }
      } else {
        totalConvictedPrisonersFailedOrSkipped++
      }
    }

    visitOrderAllocationJobService.setVisitOrderAllocationPrisonJobEndTimeAndStats(
      jobReference = jobReference,
      prisonCode = prisonId,
      totalConvictedPrisoners = allPrisoners.size,
      totalPrisonersProcessed = totalConvictedPrisonersProcessed,
      totalPrisonersFailedOrSkipped = totalConvictedPrisonersFailedOrSkipped,
    )

    LOG.info("Finished AllocationService - processPrisonAllocation with prisonCode: $prisonId, total records processed : ${allPrisoners.size}")
  }

  private fun processPrisoner(
    prisoner: AttributeSearchPrisonerDto,
    jobReference: String,
    allPrisonIncentiveAmounts: List<PrisonIncentiveAmountsDto>,
  ): UUID? = try {
    val prisonerDetails = prisonerSearchClient.getPrisonerById(prisoner.prisonerId)
    val prisonerIncentive = incentivesClient.getPrisonerIncentiveReviewHistory(prisoner.prisonerId)
    val prisonIncentiveAmounts = allPrisonIncentiveAmounts.firstOrNull { it.levelCode == prisonerIncentive.iepCode }
      ?: incentivesClient.getPrisonIncentiveLevelByLevelCode(prisonerDetails.prisonId, prisonerIncentive.iepCode)

    prisonerAllocationService.processPrisonerAllocation(
      prisonerId = prisoner.prisonerId,
      prisonIncentiveAmounts = prisonIncentiveAmounts,
      prisonerIncentiveLevel = prisonerIncentive.iepCode,
    )
  } catch (e: Exception) {
    LOG.error("Error processing prisoner - ${prisoner.prisonerId}, putting ${prisoner.prisonerId} on prisoner retry queue", e)
    prisonerRetryService.sendMessageToPrisonerRetryQueue(
      jobReference = jobReference,
      prisonerId = prisoner.prisonerId,
    )
    null
  }

  private fun getConvictedPrisonersForPrison(jobReference: String, prisonId: String): List<AttributeSearchPrisonerDto> {
    val convictedPrisonersForPrison = try {
      prisonerSearchClient.getConvictedPrisonersByPrisonId(prisonId).content.toList()
    } catch (e: Exception) {
      val failureMessage = "failed to get convicted prisoners by prisonId - $prisonId"
      LOG.error(failureMessage, e)
      visitOrderAllocationJobService.setVisitOrderAllocationPrisonJobEndTimeAndFailureMessage(jobReference, prisonId, failureMessage)
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
      visitOrderAllocationJobService.setVisitOrderAllocationPrisonJobEndTimeAndFailureMessage(jobReference, prisonId, failureMessage)
      throw e
    }

    return incentiveLevelsForPrison
  }
}
