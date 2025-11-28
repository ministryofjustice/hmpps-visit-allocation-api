package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.ChangeLogType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderHistory
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderHistoryAttributes
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderHistoryRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.ChangeLogService.Companion.LOG
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VOBalancesUtil

@Service
@Transactional
class VisitOrderHistoryService(
  private val visitOrderHistoryRepository: VisitOrderHistoryRepository,
  private val voBalancesUtil: VOBalancesUtil,
) {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
    const val SYSTEM_USER_ID = "SYSTEM"
  }

  fun createLogMigrationChange(migrationChangeDto: VisitAllocationPrisonerMigrationDto, dpsPrisoner: PrisonerDetails): VisitOrderHistory {
    LOG.info("Logging migration to visit_order_history table for prisoner ${migrationChangeDto.prisonerId}, migration - $migrationChangeDto")

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.MIGRATION,
      userName = SYSTEM_USER_ID,
      comment = "migrated prisoner from nomis to dps",
      attributes = emptyMap(),
    )
  }

  fun createLogSyncAdjustmentChange(syncDto: VisitAllocationPrisonerSyncDto, dpsPrisoner: PrisonerDetails): VisitOrderHistory {
    LOG.info("Logging sync to visit_order_history table for prisoner ${syncDto.prisonerId}, sync - $syncDto")

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.SYNC,
      userName = SYSTEM_USER_ID,
      comment = "synced prisoner with adjustment code ${syncDto.adjustmentReasonCode.name}",
      attributes = emptyMap(),
    )
  }

  fun createLogSyncEventChange(dpsPrisoner: PrisonerDetails, domainEventType: DomainEventType): VisitOrderHistory {
    LOG.info("Logging sync to visit_order_history table for prisoner ${dpsPrisoner.prisonerId}, event - ${domainEventType.value}")

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.SYNC,
      userName = SYSTEM_USER_ID,
      comment = "synced prisoner with domain event ${domainEventType.value}",
      attributes = emptyMap(),
    )
  }

  fun createLogBatchProcess(dpsPrisoner: PrisonerDetails): VisitOrderHistory {
    LOG.info("Logging sync to visit_order_history table for prisoner ${dpsPrisoner.prisonerId} - createLogBatchProcess")

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.BATCH_PROCESS,
      userName = SYSTEM_USER_ID,
      comment = "changed via nightly batch process",
      attributes = emptyMap(),
    )
  }

  fun createLogAllocationUsedByVisit(dpsPrisoner: PrisonerDetails, visitReference: String): VisitOrderHistory {
    LOG.info("Logging to visit_order_history table for prisoner ${dpsPrisoner.prisonerId} - createLogAllocationUsedByVisit")

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.ALLOCATION_USED_BY_VISIT,
      userName = SYSTEM_USER_ID,
      comment = "allocated to $visitReference",
      attributes = emptyMap(),
    )
  }

  fun createLogAllocationRefundedByVisitCancelled(dpsPrisoner: PrisonerDetails, visitReference: String): VisitOrderHistory {
    LOG.info("Logging to visit_order_history table for prisoner ${dpsPrisoner.prisonerId} - createLogAllocationRefundedByVisitCancelled")
    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.ALLOCATION_REFUNDED_BY_VISIT_CANCELLED,
      userName = SYSTEM_USER_ID,
      comment = "allocated refunded as $visitReference cancelled",
      attributes = emptyMap(),
    )
  }

  fun createLogAllocationForPrisonerMerge(dpsPrisoner: PrisonerDetails, newPrisonerId: String, removedPrisonerId: String): VisitOrderHistory {
    LOG.info("Logging to visit_order_history table for prisoner ${dpsPrisoner.prisonerId} - createLogAllocationForPrisonerMerge")

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.ALLOCATION_ADDED_AFTER_PRISONER_MERGE,
      userName = SYSTEM_USER_ID,
      comment = "allocation added as a result of prisoner $removedPrisonerId being merged into $newPrisonerId",
      attributes = emptyMap(),
    )
  }

  fun createLogPrisonerBalanceReset(dpsPrisoner: PrisonerDetails, reason: PrisonerReceivedReasonType): VisitOrderHistory {
    LOG.info("Logging to visit_order_history table for prisoner ${dpsPrisoner.prisonerId} - createLogPrisonerBalanceReset")
    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      changeLogType = ChangeLogType.PRISONER_BALANCE_RESET,
      userName = SYSTEM_USER_ID,
      comment = "prisoner balance reset for reason ${reason.name}",
      attributes = emptyMap(),
    )
  }

  private fun createVisitOrderHistory(
    dpsPrisoner: PrisonerDetails,
    changeLogType: ChangeLogType,
    userName: String,
    comment: String,
    attributes: Map<String, String>,
  ): VisitOrderHistory {
    val prisonerDetailedBalance = voBalancesUtil.getPrisonersDetailedBalance(dpsPrisoner)

    val visitOrderHistory = VisitOrderHistory(
      type = changeLogType,
      userName = userName,
      comment = comment,
      prisoner = dpsPrisoner,
      voBalance = prisonerDetailedBalance.voBalance,
      pvoBalance = prisonerDetailedBalance.pvoBalance,
    )

    attributes.forEach { attribute ->
      visitOrderHistory.visitOrderHistoryAttributes.add(
        VisitOrderHistoryAttributes(
          visitOrderHistory = visitOrderHistory,
          attributeType = attribute.key,
          attributeValue = attribute.value,
        ),
      )
    }

    return visitOrderHistoryRepository.save(visitOrderHistory)
  }
}
