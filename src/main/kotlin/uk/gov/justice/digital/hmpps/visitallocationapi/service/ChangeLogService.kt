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
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails

@Transactional
@Service
class ChangeLogService {
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
      comment = "migrated prisoner ${dpsPrisoner.prisonerId}, with vo balance ${migrationChangeDto.voBalance} and pvo balance ${migrationChangeDto.pvoBalance} and lastAllocatedDate ${migrationChangeDto.lastVoAllocationDate}",
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
      comment = "synced prisoner ${syncDto.prisonerId}, with adjustment code ${syncDto.adjustmentReasonCode.name}",
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
      comment = "synced prisoner ${dpsPrisoner.prisonerId}, with domain event ${domainEventType.value}",
      prisoner = dpsPrisoner,
      visitOrderBalance = dpsPrisoner.getVoBalance(),
      privilegedVisitOrderBalance = dpsPrisoner.getPvoBalance(),
    )
  }
}
