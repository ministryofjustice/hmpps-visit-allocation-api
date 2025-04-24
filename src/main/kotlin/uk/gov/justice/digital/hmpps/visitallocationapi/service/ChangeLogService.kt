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
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.ChangeLogRepository

@Transactional
@Service
class ChangeLogService(private val changeLogRepository: ChangeLogRepository) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun logMigrationChange(migrationChangeDto: VisitAllocationPrisonerMigrationDto) {
    LOG.info("Logging migration to change_log table for prisoner ${migrationChangeDto.prisonerId}, migration - $migrationChangeDto")
    changeLogRepository.save(
      ChangeLog(
        prisonerId = migrationChangeDto.prisonerId,
        changeType = ChangeLogType.MIGRATION,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "migrated prisoner ${migrationChangeDto.prisonerId}, with vo balance ${migrationChangeDto.voBalance} and pvo balance ${migrationChangeDto.pvoBalance} and lastAllocatedDate ${migrationChangeDto.lastVoAllocationDate}",
      ),
    )
  }

  fun logSyncAdjustmentChange(syncDto: VisitAllocationPrisonerSyncDto) {
    LOG.info("Logging sync to change_log table for prisoner ${syncDto.prisonerId}, sync - $syncDto")
    changeLogRepository.save(
      ChangeLog(
        prisonerId = syncDto.prisonerId,
        changeType = ChangeLogType.SYNC,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "synced prisoner ${syncDto.prisonerId}, with adjustment code ${syncDto.adjustmentReasonCode.name}",
      ),
    )
  }

  fun logSyncEventChange(prisonerId: String, domainEventType: DomainEventType) {
    LOG.info("Logging sync to change_log table for prisoner $prisonerId, event - ${domainEventType.value}")
    changeLogRepository.save(
      ChangeLog(
        prisonerId = prisonerId,
        changeType = ChangeLogType.SYNC,
        changeSource = ChangeLogSource.SYSTEM,
        userId = "SYSTEM",
        comment = "synced prisoner $prisonerId, with domain event ${domainEventType.value}",
      ),
    )
  }

  fun removePrisonerLogs(prisonerId: String) {
    changeLogRepository.deleteAllByPrisonerId(prisonerId)
  }
}
