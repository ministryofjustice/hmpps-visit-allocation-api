package uk.gov.justice.digital.hmpps.visitallocationapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderAllocationPrisonJob
import java.time.LocalDateTime

@Repository
interface VisitOrderAllocationPrisonJobRepository : JpaRepository<VisitOrderAllocationPrisonJob, Long> {
  @Transactional
  @Modifying
  @Query(
    value = """
        UPDATE VisitOrderAllocationPrisonJob v SET   
        v.startTimestamp = :startTimestamp
        WHERE v.prisonCode = :prisonCode 
        AND v.allocationJobReference = :allocationJobReference
    """,
  )
  fun updateStartTimestamp(
    allocationJobReference: String,
    prisonCode: String,
    startTimestamp: LocalDateTime,
  )

  @Transactional
  @Modifying
  @Query(
    value = """
        UPDATE VisitOrderAllocationPrisonJob v SET   
        v.failureMessage = :failureMessage,
        v.endTimestamp = :endTimestamp
        WHERE v.prisonCode = :prisonCode 
        AND v.allocationJobReference = :allocationJobReference
    """,
  )
  fun updateFailureMessageAndEndTimestamp(
    allocationJobReference: String,
    prisonCode: String,
    failureMessage: String,
    endTimestamp: LocalDateTime,
  )

  @Transactional
  @Modifying
  @Query(
    value = """
        UPDATE VisitOrderAllocationPrisonJob v SET   
        v.endTimestamp = :endTimestamp, 
        v.convictedPrisoners = :totalPrisoners,
        v.processedPrisoners = :processedPrisoners,
        v.failedOrSkippedPrisoners = :failedOrSkippedPrisoners
        WHERE v.prisonCode = :prisonCode 
        AND v.allocationJobReference = :allocationJobReference
    """,
  )
  fun updateEndTimestampAndStats(
    allocationJobReference: String,
    prisonCode: String,
    endTimestamp: LocalDateTime,
    totalPrisoners: Int,
    processedPrisoners: Int,
    failedOrSkippedPrisoners: Int,
  )
}
