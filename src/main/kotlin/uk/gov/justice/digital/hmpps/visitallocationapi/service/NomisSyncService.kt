package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.DomainEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.AdjustmentReasonCode
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.InvalidSyncRequestException
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate
import kotlin.math.abs

@Service
class NomisSyncService(
  private val balanceService: BalanceService,
  private val prisonerDetailsService: PrisonerDetailsService,
  private val telemetryService: TelemetryClientService,
  private val changeLogService: ChangeLogService,
  private val prisonApiClient: PrisonApiClient,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun syncPrisonerAdjustmentChanges(syncDto: VisitAllocationPrisonerSyncDto) {
    // In NOMIS, when a change is made via the adjustments_table, it will trigger a call
    // to this endpoint, allowing DPS to sync the prisoner vo & pvo balances with NOMIS.
    LOG.info("Entered NomisSyncService - syncPrisoner with sync dto {}", syncDto)

    validateSyncRequest(syncDto)

    var prisonerBalance = balanceService.getPrisonerBalance(syncDto.prisonerId)
    val dpsPrisoner: PrisonerDetails
    if (prisonerBalance != null) {
      // Only do a balance comparison if prisoner exists.
      compareBalanceBeforeSync(syncDto, prisonerBalance)
      dpsPrisoner = prisonerDetailsService.getPrisonerDetails(syncDto.prisonerId)!!
    } else {
      // If they're new, onboard them by saving their details in the prisoner_details table and init their balance.
      dpsPrisoner = prisonerDetailsService.createPrisonerDetails(syncDto.prisonerId, syncDto.createdDate, null)
      prisonerBalance = PrisonerBalanceDto(prisonerId = syncDto.prisonerId, voBalance = 0, pvoBalance = 0)
    }

    // If VO balance has changed, sync it
    if (syncDto.oldVoBalance != null) {
      processSync(
        prisoner = dpsPrisoner,
        prisonerDpsBalance = prisonerBalance.voBalance,
        balanceChange = syncDto.changeToVoBalance!!,
        visitOrderType = VisitOrderType.VO,
      )
    }

    // If PVO balance has changed, sync it
    if (syncDto.oldPvoBalance != null) {
      processSync(
        prisoner = dpsPrisoner,
        prisonerDpsBalance = prisonerBalance.pvoBalance,
        balanceChange = syncDto.changeToPvoBalance!!,
        visitOrderType = VisitOrderType.PVO,
      )
    }

    // If this is a VO allocation from the NOMIS system, update the lastVoAllocatedDate to keep this synced.
    if (syncDto.adjustmentReasonCode == AdjustmentReasonCode.IEP && syncDto.changeLogSource == ChangeLogSource.SYSTEM) {
      LOG.info("Nomis Sync reason due to IEP allocation from system, adding /update allocation date for prisoner ${syncDto.prisonerId }in our service")
      dpsPrisoner.lastVoAllocatedDate = syncDto.createdDate
    }

    dpsPrisoner.changeLogs.add(changeLogService.logSyncAdjustmentChange(syncDto, dpsPrisoner))

    prisonerDetailsService.updatePrisonerDetails(prisoner = dpsPrisoner)
  }

  @Transactional
  fun syncPrisonerBalanceFromEventChange(prisonerId: String, domainEventType: DomainEventType) {
    LOG.info("Entered NomisSyncService - syncPrisonerBalanceFromEventChange for prisoner {}", prisonerId)

    val prisonerNomisBalance = prisonApiClient.getBookingVisitBalances(prisonerId)
    if (prisonerNomisBalance == null) {
      LOG.warn("Prisoner $prisonerId balance not found on NOMIS. Skipping sync")
      return
    }

    var prisonerDpsBalance = balanceService.getPrisonerBalance(prisonerId)
    val dpsPrisoner: PrisonerDetails
    if (prisonerDpsBalance == null) {
      val lastVoAllocatedDate = prisonerNomisBalance.latestIepAdjustDate ?: LocalDate.now()
      // If they're new, onboard them by saving their details in the prisoner_details table and init their balance.
      dpsPrisoner = prisonerDetailsService.createPrisonerDetails(prisonerId, lastVoAllocatedDate, prisonerNomisBalance.latestPrivIepAdjustDate)
      prisonerDpsBalance = PrisonerBalanceDto(prisonerId, 0, 0)
    } else {
      dpsPrisoner = prisonerDetailsService.getPrisonerDetails(prisonerId)!!
    }

    val voBalanceChange = (prisonerNomisBalance.remainingVo - prisonerDpsBalance.voBalance)
    processSync(
      prisoner = dpsPrisoner,
      prisonerDpsBalance = prisonerDpsBalance.voBalance,
      balanceChange = voBalanceChange,
      visitOrderType = VisitOrderType.VO,
    )

    val pvoBalanceChange = (prisonerNomisBalance.remainingPvo - prisonerDpsBalance.pvoBalance)
    processSync(
      prisoner = dpsPrisoner,
      prisonerDpsBalance = prisonerDpsBalance.pvoBalance,
      balanceChange = pvoBalanceChange,
      visitOrderType = VisitOrderType.PVO,
    )

    if (voBalanceChange != 0 || pvoBalanceChange != 0) {
      LOG.info("Balance has changed as a result of sync for prisoner $prisonerId, for domain event ${domainEventType.value}")
      dpsPrisoner.changeLogs.add(changeLogService.logSyncEventChange(dpsPrisoner, domainEventType))
    }

    prisonerDetailsService.updatePrisonerDetails(prisoner = dpsPrisoner)
  }

  @Transactional
  fun syncPrisonerRemoved(prisonerId: String) {
    LOG.info("Entered NomisSyncService - syncPrisonerRemoved for prisoner {}", prisonerId)
    prisonerDetailsService.removePrisonerDetails(prisonerId)
  }

  private fun processSync(prisoner: PrisonerDetails, prisonerDpsBalance: Int, balanceChange: Int, visitOrderType: VisitOrderType) {
    LOG.info("Entered process Sync - $visitOrderType - for prisoner ${prisoner.prisonerId}, with a DPS balance of $prisonerDpsBalance, and a change of $balanceChange")
    when {
      prisonerDpsBalance > 0 -> {
        handlePositiveBalance(prisoner, prisonerDpsBalance, balanceChange, visitOrderType)
      }
      prisonerDpsBalance < 0 -> {
        handleNegativeBalance(prisoner, prisonerDpsBalance, balanceChange, visitOrderType)
      }
      else -> {
        handleZeroBalance(prisoner, balanceChange, visitOrderType)
      }
    }
  }

  private fun handlePositiveBalance(prisoner: PrisonerDetails, prisonerDpsBalance: Int, balanceChange: Int, visitOrderType: VisitOrderType) {
    LOG.info("Positive DPS balance, syncing with nomis for prisoner ${prisoner.prisonerId}")
    if (balanceChange >= 0) {
      LOG.info("Balance increased and remains positive for prisoner ${prisoner.prisonerId}, creating $balanceChange, $visitOrderType")
      prisoner.visitOrders.addAll(createVisitOrders(prisoner, visitOrderType, balanceChange))
    } else {
      if ((prisonerDpsBalance + balanceChange) >= 0) {
        LOG.info("Balance decreased but remains positive for prisoner ${prisoner.prisonerId}, expiring $balanceChange, $visitOrderType")
        prisoner.visitOrders
          .filter { it.type == visitOrderType && it.status in listOf(VisitOrderStatus.AVAILABLE, VisitOrderStatus.ACCUMULATED) }
          .sortedBy { it.createdTimestamp }
          .take(abs(balanceChange))
          .forEach { it.status = VisitOrderStatus.EXPIRED }
      } else {
        val negativeVosToCreate = abs(prisonerDpsBalance + balanceChange)
        LOG.info("Balance decreased and is negative for prisoner ${prisoner.prisonerId}, expiring all $visitOrderType and creating $negativeVosToCreate $visitOrderType")
        prisoner.visitOrders.filter { it.type == visitOrderType }.forEach { visitOrder -> visitOrder.status = VisitOrderStatus.EXPIRED }
        prisoner.negativeVisitOrders.addAll(createNegativeVisitOrders(prisoner, visitOrderType, negativeVosToCreate))
      }
    }
  }

  private fun handleNegativeBalance(prisoner: PrisonerDetails, prisonerDpsBalance: Int, balanceChange: Int, visitOrderType: VisitOrderType) {
    LOG.info("Negative DPS balance, syncing with nomis for prisoner ${prisoner.prisonerId}")
    if (balanceChange <= 0) {
      LOG.info("Balance decreased and remains negative for prisoner ${prisoner.prisonerId}, creating $balanceChange, $visitOrderType")
      prisoner.negativeVisitOrders.addAll(createNegativeVisitOrders(prisoner, visitOrderType, abs(balanceChange)))
    } else {
      if ((prisonerDpsBalance + balanceChange) <= 0) {
        LOG.info("Balance increased but remains negative for prisoner ${prisoner.prisonerId}, repaying $balanceChange, $visitOrderType")
        prisoner.negativeVisitOrders
          .filter { it.type == visitOrderType && it.status == NegativeVisitOrderStatus.USED }
          .sortedBy { it.createdTimestamp }
          .take(abs(balanceChange))
          .forEach { it.status = NegativeVisitOrderStatus.REPAID }
      } else {
        val positiveVosToCreate = prisonerDpsBalance + balanceChange
        LOG.info("Balance increased and is positive for prisoner ${prisoner.prisonerId}, repaying all $visitOrderType and creating $positiveVosToCreate $visitOrderType")
        prisoner.negativeVisitOrders.filter { it.type == visitOrderType }.forEach { visitOrder -> visitOrder.status = NegativeVisitOrderStatus.REPAID }
        prisoner.visitOrders.addAll(createVisitOrders(prisoner, visitOrderType, positiveVosToCreate))
      }
    }
  }

  private fun handleZeroBalance(prisoner: PrisonerDetails, balanceChange: Int, visitOrderType: VisitOrderType) {
    LOG.info("Zero DPS balance, syncing with nomis for prisoner ${prisoner.prisonerId}")
    if (balanceChange >= 0) {
      LOG.info("Balance increased for prisoner ${prisoner.prisonerId}, creating $balanceChange $visitOrderType")
      prisoner.visitOrders.addAll(createVisitOrders(prisoner, visitOrderType, balanceChange))
    } else {
      LOG.info("Balance decreased for prisoner ${prisoner.prisonerId}, creating $balanceChange $visitOrderType")
      prisoner.negativeVisitOrders.addAll(createNegativeVisitOrders(prisoner, visitOrderType, abs(balanceChange)))
    }
  }

  private fun createVisitOrders(prisoner: PrisonerDetails, visitOrderType: VisitOrderType, amountToCreate: Int): List<VisitOrder> {
    val visitOrders = mutableListOf<VisitOrder>()
    repeat(amountToCreate) {
      visitOrders.add(
        VisitOrder(
          prisonerId = prisoner.prisonerId,
          type = visitOrderType,
          status = VisitOrderStatus.AVAILABLE,
          prisoner = prisoner,
        ),
      )
    }
    return visitOrders
  }

  private fun createNegativeVisitOrders(prisoner: PrisonerDetails, negativeVoType: VisitOrderType, amountToCreate: Int): List<NegativeVisitOrder> {
    val negativeVisitOrders = mutableListOf<NegativeVisitOrder>()
    repeat(amountToCreate) {
      negativeVisitOrders.add(
        NegativeVisitOrder(
          prisonerId = prisoner.prisonerId,
          type = negativeVoType,
          status = NegativeVisitOrderStatus.USED,
          prisoner = prisoner,
        ),
      )
    }
    return negativeVisitOrders
  }

  private fun compareBalanceBeforeSync(syncDto: VisitAllocationPrisonerSyncDto, prisonerBalance: PrisonerBalanceDto) {
    if (syncDto.oldVoBalance != null) {
      if (prisonerBalance.voBalance != syncDto.oldVoBalance) {
        LOG.error("Discovered discrepancy between NOMIS and DPS VO balances. Logging error to application insights for prisoner ${syncDto.prisonerId}")
        val telemetryBalanceProperties = mapOf(
          "prisonerId" to syncDto.prisonerId,
          "nomisVoBalance" to syncDto.oldVoBalance.toString(),
          "dpsVoBalance" to prisonerBalance.voBalance.toString(),
        )
        telemetryService.trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, telemetryBalanceProperties)
      }
    }

    if (syncDto.oldPvoBalance != null) {
      if (prisonerBalance.pvoBalance != syncDto.oldPvoBalance) {
        LOG.error("Discovered discrepancy between NOMIS and DPS PVO balances. Logging error to application insights for prisoner ${syncDto.prisonerId}")
        val telemetryBalanceProperties = mapOf(
          "prisonerId" to syncDto.prisonerId,
          "nomisPvoBalance" to syncDto.oldPvoBalance.toString(),
          "dpsPvoBalance" to prisonerBalance.pvoBalance.toString(),
        )
        telemetryService.trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, telemetryBalanceProperties)
      }
    }
  }

  private fun validateSyncRequest(syncDto: VisitAllocationPrisonerSyncDto) {
    LOG.info("Entered NomisSyncService - validateSyncRequest")
    val invalidVoRequest = syncDto.oldVoBalance == null && syncDto.changeToVoBalance != null
    val invalidPvoRequest = syncDto.oldPvoBalance == null && syncDto.changeToPvoBalance != null
    if (invalidVoRequest || invalidPvoRequest) {
      LOG.error("Invalid sync request found, throwing InvalidSyncRequestException exception - $syncDto")
      throw InvalidSyncRequestException("Balance is null but change to balance found in request - $syncDto")
    }
  }
}
