package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.VisitOrderHistoryDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.AllocationBatchProcessType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.AllocationBatchProcessType.ACCUMULATION
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.AllocationBatchProcessType.ALLOCATION
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.AllocationBatchProcessType.EXPIRATION
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryAttributeType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType.PVO
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType.VO
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.PrisonerReceivedReasonType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderHistory
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderHistoryAttributes
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderHistoryRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VOBalancesUtil
import java.time.LocalDate

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

  fun getVisitOrderHistoryForPrisoner(prisonerId: String, fromDate: LocalDate): List<VisitOrderHistoryDto> = visitOrderHistoryRepository.findVisitOrderHistoryByPrisonerIdAfterFromDateOrderByIdAsc(prisonerId, fromDate.atStartOfDay()).map { VisitOrderHistoryDto(it) }

  fun logMigrationChange(migrationChangeDto: VisitAllocationPrisonerMigrationDto, dpsPrisoner: PrisonerDetails): VisitOrderHistory {
    logger.info("Logging migration to visit_order_history table for prisoner ${migrationChangeDto.prisonerId}, migration - $migrationChangeDto")

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      visitOrderHistoryType = VisitOrderHistoryType.MIGRATION,
      userName = SYSTEM_USER_ID,
      attributes = emptyMap(),
    )
  }

  fun logSyncAdjustmentChange(syncDto: VisitAllocationPrisonerSyncDto, dpsPrisoner: PrisonerDetails): VisitOrderHistory {
    logger.info("Logging sync to visit_order_history table for prisoner ${syncDto.prisonerId}, sync - $syncDto")

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      visitOrderHistoryType = VisitOrderHistoryType.SYNC_FROM_NOMIS,
      userName = SYSTEM_USER_ID,
      attributes = emptyMap(),
    )
  }

  fun logSyncEventChange(dpsPrisoner: PrisonerDetails, domainEventType: DomainEventType): VisitOrderHistory {
    logger.info("Logging sync to visit_order_history table for prisoner ${dpsPrisoner.prisonerId}, event - ${domainEventType.value}")

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      visitOrderHistoryType = VisitOrderHistoryType.SYNC_FROM_NOMIS,
      userName = SYSTEM_USER_ID,
      attributes = emptyMap(),
    )
  }

  fun logBatchProcess(
    dpsPrisoner: PrisonerDetails,
    allocationBatchProcessType: AllocationBatchProcessType,
    visitOrderTypes: Set<VisitOrderType>,
    prisonerIncentiveLevel: String? = null,
  ): VisitOrderHistory {
    logger.info("Logging sync to visit_order_history table for prisoner ${dpsPrisoner.prisonerId}, allocation batch process $allocationBatchProcessType - logBatchProcess")
    val visitOrderHistoryType = getVisitHistoryType(allocationBatchProcessType, visitOrderTypes)
    val attributes = prisonerIncentiveLevel?.let { mapOf(VisitOrderHistoryAttributeType.INCENTIVE_LEVEL to prisonerIncentiveLevel) } ?: emptyMap()

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      visitOrderHistoryType = visitOrderHistoryType,
      userName = SYSTEM_USER_ID,
      attributes = attributes,
    )
  }

  fun logAllocationUsedByVisit(dpsPrisoner: PrisonerDetails, visitReference: String): VisitOrderHistory {
    logger.info("Logging to visit_order_history table for prisoner ${dpsPrisoner.prisonerId} - logAllocationUsedByVisit")
    val attributes = mapOf(VisitOrderHistoryAttributeType.VISIT_REFERENCE to visitReference)

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      visitOrderHistoryType = VisitOrderHistoryType.ALLOCATION_USED_BY_VISIT,
      userName = SYSTEM_USER_ID,
      attributes = attributes,
    )
  }

  fun logAllocationRefundedByVisitCancelled(dpsPrisoner: PrisonerDetails, visitReference: String): VisitOrderHistory {
    logger.info("Logging to visit_order_history table for prisoner ${dpsPrisoner.prisonerId} - logAllocationRefundedByVisitCancelled")
    val attributes = mapOf(VisitOrderHistoryAttributeType.VISIT_REFERENCE to visitReference)

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      visitOrderHistoryType = VisitOrderHistoryType.ALLOCATION_REFUNDED_BY_VISIT_CANCELLED,
      userName = SYSTEM_USER_ID,
      attributes = attributes,
    )
  }

  fun logAllocationForPrisonerMerge(dpsPrisoner: PrisonerDetails, newPrisonerId: String, removedPrisonerId: String): VisitOrderHistory {
    logger.info("Logging to visit_order_history table for prisoner ${dpsPrisoner.prisonerId} - logAllocationForPrisonerMerge")

    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      visitOrderHistoryType = VisitOrderHistoryType.ALLOCATION_ADDED_AFTER_PRISONER_MERGE,
      userName = SYSTEM_USER_ID,
      attributes = mapOf(
        VisitOrderHistoryAttributeType.NEW_PRISONER_ID to newPrisonerId,
        VisitOrderHistoryAttributeType.OLD_PRISONER_ID to removedPrisonerId,
      ),
    )
  }

  fun logPrisonerBalanceReset(dpsPrisoner: PrisonerDetails, reason: PrisonerReceivedReasonType): VisitOrderHistory {
    logger.info("Logging to visit_order_history table for prisoner ${dpsPrisoner.prisonerId} - logPrisonerBalanceReset")
    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      visitOrderHistoryType = VisitOrderHistoryType.PRISONER_BALANCE_RESET,
      userName = SYSTEM_USER_ID,
      attributes = emptyMap(),
    )
  }

  fun logPrisonerNegativeBalanceAdminReset(dpsPrisoner: PrisonerDetails): VisitOrderHistory {
    logger.info("Logging to change_log table for prisoner ${dpsPrisoner.prisonerId} - createLogPrisonerNegativeBalanceAdminReset")
    return createVisitOrderHistory(
      dpsPrisoner = dpsPrisoner,
      visitOrderHistoryType = VisitOrderHistoryType.ADMIN_RESET_NEGATIVE_BALANCE,
      userName = SYSTEM_USER_ID,
      attributes = emptyMap(),
    )
  }
  private fun getVisitHistoryType(batchProcessType: AllocationBatchProcessType, visitOrderTypes: Set<VisitOrderType>): VisitOrderHistoryType {
    val hasVOAndPVO: Boolean = visitOrderTypes.containsAll(listOf(VO, PVO))
    val hasVOOnly: Boolean = visitOrderTypes.contains(VO) && !visitOrderTypes.contains(PVO)
    val hasPvoOnly: Boolean = !visitOrderTypes.contains(VO) && visitOrderTypes.contains(PVO)

    return when {
      (batchProcessType == ALLOCATION && hasVOOnly) -> VisitOrderHistoryType.VO_ALLOCATION
      (batchProcessType == ALLOCATION && hasPvoOnly) -> VisitOrderHistoryType.PVO_ALLOCATION
      (batchProcessType == ALLOCATION && hasVOAndPVO) -> VisitOrderHistoryType.VO_AND_PVO_ALLOCATION
      (batchProcessType == EXPIRATION && hasVOOnly) -> VisitOrderHistoryType.VO_EXPIRATION
      (batchProcessType == EXPIRATION && hasPvoOnly) -> VisitOrderHistoryType.PVO_EXPIRATION
      (batchProcessType == EXPIRATION && hasVOAndPVO) -> VisitOrderHistoryType.VO_AND_PVO_EXPIRATION
      (batchProcessType == ACCUMULATION) -> VisitOrderHistoryType.VO_ACCUMULATION
      else -> throw IllegalArgumentException("Invalid combination of batch process type - $batchProcessType and visit order types - $visitOrderTypes")
    }
  }

  private fun createVisitOrderHistory(
    dpsPrisoner: PrisonerDetails,
    visitOrderHistoryType: VisitOrderHistoryType,
    userName: String,
    comment: String? = null,
    attributes: Map<VisitOrderHistoryAttributeType, String>,
  ): VisitOrderHistory {
    val prisonerDetailedBalance = voBalancesUtil.getPrisonersDetailedBalance(dpsPrisoner)

    val visitOrderHistory = VisitOrderHistory(
      type = visitOrderHistoryType,
      userName = userName,
      prisoner = dpsPrisoner,
      voBalance = prisonerDetailedBalance.voBalance,
      pvoBalance = prisonerDetailedBalance.pvoBalance,
      comment = comment,
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
