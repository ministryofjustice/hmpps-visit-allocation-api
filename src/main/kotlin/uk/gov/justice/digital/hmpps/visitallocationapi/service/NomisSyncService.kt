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
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import java.time.LocalDate
import kotlin.math.abs

@Service
class NomisSyncService(
  private val balanceService: BalanceService,
  private val prisonerDetailsService: PrisonerDetailsService,
  private val telemetryService: TelemetryClientService,
  private val visitOrderRepository: VisitOrderRepository,
  private val negativeVisitOrderRepository: NegativeVisitOrderRepository,
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
      dpsPrisoner = prisonerDetailsService.getPrisoner(syncDto.prisonerId)!!
    } else {
      // If they're new, onboard them by saving their details in the prisoner_details table and init their balance.
      dpsPrisoner = prisonerDetailsService.createNewPrisonerDetails(syncDto.prisonerId, syncDto.createdDate, null)
      prisonerBalance = PrisonerBalanceDto(prisonerId = syncDto.prisonerId, voBalance = 0, pvoBalance = 0)
    }

    // If VO balance has changed, sync it
    if (syncDto.oldVoBalance != null) {
      processSync(
        prisonerId = syncDto.prisonerId,
        prisonerDpsBalance = prisonerBalance.voBalance,
        balanceChange = syncDto.changeToVoBalance!!,
        visitOrderType = VisitOrderType.VO,
      )
    }

    // If PVO balance has changed, sync it
    if (syncDto.oldPvoBalance != null) {
      processSync(
        prisonerId = syncDto.prisonerId,
        prisonerDpsBalance = prisonerBalance.pvoBalance,
        balanceChange = syncDto.changeToPvoBalance!!,
        visitOrderType = VisitOrderType.PVO,
      )
    }

    // If this is a VO allocation from the NOMIS system, update the lastVoAllocatedDate to keep this synced.
    if (syncDto.adjustmentReasonCode == AdjustmentReasonCode.IEP && syncDto.changeLogSource == ChangeLogSource.SYSTEM) {
      LOG.info("Nomis Sync reason due to IEP allocation from system, adding /update allocation date for prisoner ${syncDto.prisonerId }in our service")
      prisonerDetailsService.updateVoLastCreatedDate(syncDto.prisonerId, syncDto.createdDate)
    }

    changeLogService.logSyncAdjustmentChange(syncDto, dpsPrisoner)
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
      dpsPrisoner = prisonerDetailsService.createNewPrisonerDetails(prisonerId, lastVoAllocatedDate, prisonerNomisBalance.latestPrivIepAdjustDate)
      prisonerDpsBalance = PrisonerBalanceDto(prisonerId, 0, 0)
    } else {
      dpsPrisoner = prisonerDetailsService.getPrisoner(prisonerId)!!
    }

    val voBalanceChange = (prisonerNomisBalance.remainingVo - prisonerDpsBalance.voBalance)
    processSync(
      prisonerId = prisonerId,
      prisonerDpsBalance = prisonerDpsBalance.voBalance,
      balanceChange = voBalanceChange,
      visitOrderType = VisitOrderType.VO,
    )

    val pvoBalanceChange = (prisonerNomisBalance.remainingPvo - prisonerDpsBalance.pvoBalance)
    processSync(
      prisonerId = prisonerId,
      prisonerDpsBalance = prisonerDpsBalance.pvoBalance,
      balanceChange = pvoBalanceChange,
      visitOrderType = VisitOrderType.PVO,
    )

    if (voBalanceChange != 0 || pvoBalanceChange != 0) {
      LOG.info("Balance has changed as a result of sync for prisoner $prisonerId, for domain event ${domainEventType.value}")
      changeLogService.logSyncEventChange(prisonerId, domainEventType, dpsPrisoner)
    }
  }

  @Transactional
  fun syncPrisonerRemoved(prisonerId: String) {
    LOG.info("Entered NomisSyncService - syncPrisonerRemoved for prisoner {}", prisonerId)

    visitOrderRepository.deleteAllByPrisonerId(prisonerId)
    negativeVisitOrderRepository.deleteAllByPrisonerId(prisonerId)
    changeLogService.removePrisonerLogs(prisonerId)
    prisonerDetailsService.removePrisonerDetails(prisonerId)
  }

  private fun processSync(prisonerId: String, prisonerDpsBalance: Int, balanceChange: Int, visitOrderType: VisitOrderType) {
    LOG.info("Entered process Sync - $visitOrderType - for prisoner $prisonerId, with a DPS balance of $prisonerDpsBalance, and a change of $balanceChange")
    when {
      prisonerDpsBalance > 0 -> {
        handlePositiveBalance(prisonerId, prisonerDpsBalance, balanceChange, visitOrderType)
      }
      prisonerDpsBalance < 0 -> {
        handleNegativeBalance(prisonerId, prisonerDpsBalance, balanceChange, visitOrderType)
      }
      else -> {
        handleZeroBalance(prisonerId, balanceChange, visitOrderType)
      }
    }
  }

  private fun handlePositiveBalance(prisonerId: String, prisonerDpsBalance: Int, balanceChange: Int, visitOrderType: VisitOrderType) {
    LOG.info("Positive DPS balance, syncing with nomis for prisoner $prisonerId")
    if (balanceChange >= 0) {
      LOG.info("Balance increased and remains positive for prisoner $prisonerId, creating $balanceChange, $visitOrderType")
      createAndSaveVisitOrders(prisonerId, visitOrderType, balanceChange)
    } else {
      if ((prisonerDpsBalance + balanceChange) >= 0) {
        LOG.info("Balance decreased but remains positive for prisoner $prisonerId, expiring $balanceChange, $visitOrderType")
        visitOrderRepository.expireVisitOrdersGivenAmount(prisonerId, visitOrderType, abs(balanceChange).toLong())
      } else {
        val negativeVosToCreate = abs(prisonerDpsBalance + balanceChange)
        LOG.info("Balance decreased and is negative for prisoner $prisonerId, expiring all $visitOrderType and creating $negativeVosToCreate $visitOrderType")
        visitOrderRepository.expireVisitOrdersGivenAmount(prisonerId, visitOrderType, null)
        createAndSaveNegativeVisitOrders(prisonerId, visitOrderType, negativeVosToCreate)
      }
    }
  }

  private fun handleNegativeBalance(prisonerId: String, prisonerDpsBalance: Int, balanceChange: Int, visitOrderType: VisitOrderType) {
    LOG.info("Negative DPS balance, syncing with nomis for prisoner $prisonerId")
    if (balanceChange <= 0) {
      LOG.info("Balance decreased and remains negative for prisoner $prisonerId, creating $balanceChange, $visitOrderType")
      createAndSaveNegativeVisitOrders(prisonerId, visitOrderType, abs(balanceChange))
    } else {
      if ((prisonerDpsBalance + balanceChange) <= 0) {
        LOG.info("Balance increased but remains negative for prisoner $prisonerId, repaying $balanceChange, $visitOrderType")
        negativeVisitOrderRepository.repayNegativeVisitOrdersGivenAmount(prisonerId, visitOrderType, abs(balanceChange).toLong())
      } else {
        val positiveVosToCreate = prisonerDpsBalance + balanceChange
        LOG.info("Balance increased and is positive for prisoner $prisonerId, repaying all $visitOrderType and creating $positiveVosToCreate $visitOrderType")
        negativeVisitOrderRepository.repayNegativeVisitOrdersGivenAmount(prisonerId, visitOrderType, null)
        createAndSaveVisitOrders(prisonerId, visitOrderType, positiveVosToCreate)
      }
    }
  }

  private fun handleZeroBalance(prisonerId: String, balanceChange: Int, visitOrderType: VisitOrderType) {
    LOG.info("Zero DPS balance, syncing with nomis for prisoner $prisonerId")
    if (balanceChange >= 0) {
      LOG.info("Balance increased for prisoner $prisonerId, creating $balanceChange $visitOrderType")
      createAndSaveVisitOrders(prisonerId, visitOrderType, balanceChange)
    } else {
      LOG.info("Balance decreased for prisoner $prisonerId, creating $balanceChange $visitOrderType")
      createAndSaveNegativeVisitOrders(prisonerId, visitOrderType, abs(balanceChange))
    }
  }

  private fun createAndSaveVisitOrders(prisonerId: String, visitOrderType: VisitOrderType, amountToCreate: Int) {
    val prisonerDetails = prisonerDetailsService.getPrisoner(prisonerId)!!
    val visitOrders = mutableListOf<VisitOrder>()
    repeat(amountToCreate) {
      visitOrders.add(
        VisitOrder(
          prisonerId = prisonerDetails.prisonerId,
          type = visitOrderType,
          status = VisitOrderStatus.AVAILABLE,
          prisoner = prisonerDetails,
        ),
      )
    }
    visitOrderRepository.saveAll(visitOrders)
  }

  private fun createAndSaveNegativeVisitOrders(prisonerId: String, negativeVoType: VisitOrderType, amountToCreate: Int) {
    val prisonerDetails = prisonerDetailsService.getPrisoner(prisonerId)!!
    val negativeVisitOrders = mutableListOf<NegativeVisitOrder>()
    repeat(amountToCreate) {
      negativeVisitOrders.add(
        NegativeVisitOrder(
          prisonerId = prisonerDetails.prisonerId,
          type = negativeVoType,
          status = NegativeVisitOrderStatus.USED,
          prisoner = prisonerDetails,
        ),
      )
    }
    negativeVisitOrderRepository.saveAll(negativeVisitOrders)
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
