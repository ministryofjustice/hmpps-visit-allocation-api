package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerAdjustmentRequestDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerAdjustmentResponseDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.NotFoundException

@Service
class NomisService(
  private val nomisSyncService: NomisSyncService,
  private val nomisMigrationService: NomisMigrationService,
  private val changeLogService: ChangeLogService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun processSyncRequest(syncDto: VisitAllocationPrisonerSyncDto) {
    nomisSyncService.syncPrisonerAdjustmentChanges(syncDto)
  }

  fun processMigrationRequest(migrationDto: VisitAllocationPrisonerMigrationDto) {
    nomisMigrationService.migratePrisoner(migrationDto)
  }

  fun getChangeLogForNomis(requestDto: VisitAllocationPrisonerAdjustmentRequestDto): VisitAllocationPrisonerAdjustmentResponseDto {
    val prisonerChangeLogs = changeLogService.findAllChangeLogsForPrisoner(requestDto.prisonerId).sortedBy { it.id }

    val currentIndex = prisonerChangeLogs.indexOfFirst { it.id == requestDto.changeLogId }
    if (currentIndex == -1) {
      throw NotFoundException("Change log with ID ${requestDto.changeLogId} not found for prisoner ${requestDto.prisonerId}")
    }

    val currentEntry = prisonerChangeLogs[currentIndex]
    val previousEntry = if (currentIndex > 0) prisonerChangeLogs[currentIndex - 1] else null

    val previousVoBalance = previousEntry?.visitOrderBalance ?: 0
    val previousPvoBalance = previousEntry?.privilegedVisitOrderBalance ?: 0

    return VisitAllocationPrisonerAdjustmentResponseDto(
      prisonerId = requestDto.prisonerId,
      voBalance = previousVoBalance,
      changeToVoBalance = currentEntry.visitOrderBalance - previousVoBalance,
      pvoBalance = previousPvoBalance,
      changeToPvoBalance = currentEntry.privilegedVisitOrderBalance - previousPvoBalance,
      changeLogType = currentEntry.changeType,
      userId = currentEntry.userId,
      changeLogSource = currentEntry.changeSource,
      changeTimestamp = currentEntry.changeTimestamp,
      comment = currentEntry.comment,
    )
  }
}
