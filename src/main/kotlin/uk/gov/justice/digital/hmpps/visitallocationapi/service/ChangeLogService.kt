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
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.ChangeLogRepository

@Transactional
@Service
class ChangeLogService(val changeLogRepository: ChangeLogRepository) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createLogMigrationChange(migrationChangeDto: VisitAllocationPrisonerMigrationDto, dpsPrisoner: PrisonerDetails): ChangeLog {
    LOG.info("Logging migration to change_log table for prisoner ${migrationChangeDto.prisonerId}, migration - $migrationChangeDto")
    return ChangeLog(
      prisonerId = dpsPrisoner.prisonerId,
      changeType = ChangeLogType.MIGRATION,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "migrated prisoner from nomis to dps",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
    )
  }

  fun createLogSyncAdjustmentChange(syncDto: VisitAllocationPrisonerSyncDto, dpsPrisoner: PrisonerDetails): ChangeLog {
    LOG.info("Logging sync to change_log table for prisoner ${syncDto.prisonerId}, sync - $syncDto")
    return ChangeLog(
      prisonerId = dpsPrisoner.prisonerId,
      changeType = ChangeLogType.SYNC,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "synced prisoner with adjustment code ${syncDto.adjustmentReasonCode.name}",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
    )
  }

  fun createLogSyncEventChange(dpsPrisoner: PrisonerDetails, domainEventType: DomainEventType): ChangeLog {
    LOG.info("Logging sync to change_log table for prisoner ${dpsPrisoner.prisonerId}, event - ${domainEventType.value}")
    return ChangeLog(
      prisonerId = dpsPrisoner.prisonerId,
      changeType = ChangeLogType.SYNC,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "synced prisoner with domain event ${domainEventType.value}",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
    )
  }

  fun createLogBatchProcess(dpsPrisoner: PrisonerDetails): ChangeLog {
    LOG.info("Logging sync to change_log table for prisoner ${dpsPrisoner.prisonerId} - createLogBatchProcess")
    return ChangeLog(
      prisonerId = dpsPrisoner.prisonerId,
      changeType = ChangeLogType.BATCH_PROCESS,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "changed via nightly batch process",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
    )
  }

  fun createLogAllocationUsedByVisit(dpsPrisoner: PrisonerDetails, visitReference: String): ChangeLog {
    LOG.info("Logging to change_log table for prisoner ${dpsPrisoner.prisonerId} - createLogAllocationUsedByVisit")
    return ChangeLog(
      prisonerId = dpsPrisoner.prisonerId,
      changeType = ChangeLogType.ALLOCATION_USED_BY_VISIT,
      changeSource = ChangeLogSource.SYSTEM,
      userId = "SYSTEM",
      comment = "allocated to $visitReference",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
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

  fun getChangeLogForPrisonerByType(prisonerId: String, changeLogType: ChangeLogType): ChangeLog? = changeLogRepository.findFirstByPrisonerIdAndChangeTypeOrderByChangeTimestampDesc(prisonerId, changeLogType)
}
