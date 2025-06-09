package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.ChangeLogRepository
import java.util.*

@Transactional
@Service
class ChangeLogService(val changeLogRepository: ChangeLogRepository) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val CHANGE_LOG_SYSTEM_USER_ID = "SYSTEM"
  }

  fun createLogMigrationChange(migrationChangeDto: VisitAllocationPrisonerMigrationDto, dpsPrisoner: PrisonerDetails): ChangeLog {
    LOG.info("Logging migration to change_log table for prisoner ${migrationChangeDto.prisonerId}, migration - $migrationChangeDto")

    return createChangeLog(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.MIGRATION,
      changeLogSource = ChangeLogSource.SYSTEM,
      userId = CHANGE_LOG_SYSTEM_USER_ID,
      comment = "migrated prisoner from nomis to dps",
    )
  }

  fun createLogSyncAdjustmentChange(syncDto: VisitAllocationPrisonerSyncDto, dpsPrisoner: PrisonerDetails): ChangeLog {
    LOG.info("Logging sync to change_log table for prisoner ${syncDto.prisonerId}, sync - $syncDto")

    return createChangeLog(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.SYNC,
      changeLogSource = ChangeLogSource.SYSTEM,
      userId = CHANGE_LOG_SYSTEM_USER_ID,
      comment = "synced prisoner with adjustment code ${syncDto.adjustmentReasonCode.name}",
    )
  }

  fun createLogSyncEventChange(dpsPrisoner: PrisonerDetails, domainEventType: DomainEventType): ChangeLog {
    LOG.info("Logging sync to change_log table for prisoner ${dpsPrisoner.prisonerId}, event - ${domainEventType.value}")

    return createChangeLog(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.SYNC,
      changeLogSource = ChangeLogSource.SYSTEM,
      userId = CHANGE_LOG_SYSTEM_USER_ID,
      comment = "synced prisoner with domain event ${domainEventType.value}",
    )
  }

  fun createLogBatchProcess(dpsPrisoner: PrisonerDetails): ChangeLog {
    LOG.info("Logging sync to change_log table for prisoner ${dpsPrisoner.prisonerId} - createLogBatchProcess")

    return createChangeLog(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.BATCH_PROCESS,
      changeLogSource = ChangeLogSource.SYSTEM,
      userId = CHANGE_LOG_SYSTEM_USER_ID,
      comment = "changed via nightly batch process",
    )
  }

  fun createLogAllocationUsedByVisit(dpsPrisoner: PrisonerDetails, visitReference: String): ChangeLog {
    LOG.info("Logging to change_log table for prisoner ${dpsPrisoner.prisonerId} - createLogAllocationUsedByVisit")

    return createChangeLog(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.ALLOCATION_USED_BY_VISIT,
      changeLogSource = ChangeLogSource.SYSTEM,
      userId = CHANGE_LOG_SYSTEM_USER_ID,
      comment = "allocated to $visitReference",
    )
  }

  fun createLogAllocationRefundedByVisitCancelled(dpsPrisoner: PrisonerDetails, visitReference: String): ChangeLog {
    LOG.info("Logging to change_log table for prisoner ${dpsPrisoner.prisonerId} - createLogAllocationRefundedByVisitCancelled")
    return createChangeLog(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.ALLOCATION_REFUNDED_BY_VISIT_CANCELLED,
      changeLogSource = ChangeLogSource.SYSTEM,
      userId = CHANGE_LOG_SYSTEM_USER_ID,
      comment = "allocated refunded as $visitReference cancelled",
    )
  }

  fun createLogAllocationForPrisonerMerge(dpsPrisoner: PrisonerDetails, newPrisonerId: String, removedPrisonerId: String): ChangeLog {
    LOG.info("Logging to change_log table for prisoner ${dpsPrisoner.prisonerId} - createLogAllocationForPrisonerMerge")

    return createChangeLog(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.ALLOCATION_ADDED_AFTER_PRISONER_MERGE,
      changeLogSource = ChangeLogSource.SYSTEM,
      userId = CHANGE_LOG_SYSTEM_USER_ID,
      comment = "allocation added as a result of prisoner $removedPrisonerId being merged into $newPrisonerId",
    )
  }

  fun createLogPrisonerBalanceReset(dpsPrisoner: PrisonerDetails, reason: PrisonerReceivedReasonType): ChangeLog {
    LOG.info("Logging to change_log table for prisoner ${dpsPrisoner.prisonerId} - createLogPrisonerBalanceReset")
    return createChangeLog(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.PRISONER_BALANCE_RESET,
      changeLogSource = ChangeLogSource.SYSTEM,
      userId = CHANGE_LOG_SYSTEM_USER_ID,
      comment = "prisoner balance reset for reason ${reason.name}",
    )
  }

  fun findAllChangeLogsForPrisoner(prisonerId: String): List<ChangeLog> {
    LOG.info("ChangeLogService - findAllChangeLogsForPrisoner called with prisonerId - $prisonerId")
    val prisonerChangeLogs = changeLogRepository.findAllByPrisonerId(prisonerId)
    if (prisonerChangeLogs.isNullOrEmpty()) {
      throw NotFoundException("No change logs found for prisoner $prisonerId")
    }

    return prisonerChangeLogs
  }

  fun findChangeLogForPrisonerByType(prisonerId: String, changeLogType: ChangeLogType): ChangeLog? = changeLogRepository.findFirstByPrisonerIdAndChangeTypeOrderByChangeTimestampDesc(prisonerId, changeLogType)

  fun findChangeLogForPrisonerByReference(prisonerId: String, reference: UUID): ChangeLog? = changeLogRepository.findFirstByPrisonerIdAndReference(prisonerId, reference)

  private fun createChangeLog(
    dpsPrisoner: PrisonerDetails,
    changeLogType: ChangeLogType,
    changeLogSource: ChangeLogSource,
    userId: String,
    comment: String,
  ) = ChangeLog(
    prisonerId = dpsPrisoner.prisonerId,
    changeType = changeLogType,
    changeSource = changeLogSource,
    userId = userId,
    comment = comment,
    prisoner = dpsPrisoner,
    visitOrderBalance = dpsPrisoner.getVoBalance(),
    privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
    reference = UUID.randomUUID(),
  )
}
