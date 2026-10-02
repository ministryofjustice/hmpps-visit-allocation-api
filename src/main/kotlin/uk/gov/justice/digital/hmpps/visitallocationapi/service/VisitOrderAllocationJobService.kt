package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationJob
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationPrisonJob
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationJobRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderAllocationPrisonJobRepository
import java.time.LocalDateTime

@Service
class VisitOrderAllocationJobService(
  private val visitOrderAllocationJobRepository: VisitOrderAllocationJobRepository,
  private val visitOrderAllocationPrisonJobRepository: VisitOrderAllocationPrisonJobRepository,
) {
  @Transactional
  fun createAllocationJob(prisonCodes: List<String>): String {
    val allocationJob = visitOrderAllocationJobRepository.save(VisitOrderAllocationJob(totalPrisons = prisonCodes.size))
    prisonCodes.forEach { prisonCode ->
      visitOrderAllocationPrisonJobRepository.save(VisitOrderAllocationPrisonJob(allocationJob.reference, prisonCode))
    }
    return allocationJob.reference
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun setVisitOrderAllocationPrisonJobStartTime(jobReference: String, prisonCode: String) {
    visitOrderAllocationPrisonJobRepository.updateStartTimestamp(jobReference, prisonCode, LocalDateTime.now())
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun setVisitOrderAllocationPrisonJobEndTimeAndFailureMessage(jobReference: String, prisonCode: String, failureMessage: String) {
    visitOrderAllocationPrisonJobRepository.updateFailureMessageAndEndTimestamp(allocationJobReference = jobReference, prisonCode = prisonCode, failureMessage, LocalDateTime.now())
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun setVisitOrderAllocationPrisonJobEndTimeAndStats(jobReference: String, prisonCode: String, totalConvictedPrisoners: Int, totalPrisonersProcessed: Int, totalPrisonersFailedOrSkipped: Int) {
    visitOrderAllocationPrisonJobRepository.updateEndTimestampAndStats(allocationJobReference = jobReference, prisonCode = prisonCode, LocalDateTime.now(), totalPrisoners = totalConvictedPrisoners, processedPrisoners = totalPrisonersProcessed, failedOrSkippedPrisoners = totalPrisonersFailedOrSkipped)
  }
}
