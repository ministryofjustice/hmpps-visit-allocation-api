package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.AdjustmentReasonCode
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import kotlin.math.abs

@Service
class NomisSyncService(
  val balanceService: BalanceService,
  val prisonerDetailsService: PrisonerDetailsService,
  val telemetryService: TelemetryClientService,
  val visitOrderRepository: VisitOrderRepository,
  val negativeVisitOrderRepository: NegativeVisitOrderRepository,
  val changeLogService: ChangeLogService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun syncPrisoner(syncDto: VisitAllocationPrisonerSyncDto) {
    LOG.info("Entered NomisSyncService - syncPrisoner with sync dto {}", syncDto)

    val prisonerBalance: PrisonerBalanceDto
    if (prisonerDetailsService.getPrisoner(syncDto.prisonerId) != null) {
      // If prisoner is existing, get their balance and do a comparison validation.
      prisonerBalance = balanceService.getPrisonerBalance(syncDto.prisonerId)
      compareBalanceBeforeSync(syncDto, prisonerBalance)
    } else {
      // If they don't exist in our system we initialise their balance to 0 ready for sync.
      prisonerBalance = PrisonerBalanceDto(prisonerId = syncDto.prisonerId, 0, 0)
    }

    // If VO balance has changed, sync it
    val wantedVoChange = calculateAmountToChange(prisonerBalance.voBalance, syncDto.oldVoBalance, syncDto.changeToVoBalance ?: 0)
    processSync(syncDto.prisonerId, prisonerBalance.voBalance, wantedVoChange, VisitOrderType.VO, NegativeVisitOrderType.NEGATIVE_VO)

    // If PVO balance has changed, sync it
    val wantedPvoChange = calculateAmountToChange(prisonerBalance.pvoBalance, syncDto.oldPvoBalance, syncDto.changeToPvoBalance ?: 0)
    processSync(syncDto.prisonerId, prisonerBalance.pvoBalance, wantedPvoChange, VisitOrderType.PVO, NegativeVisitOrderType.NEGATIVE_PVO)

    // If this is a VO allocation from the NOMIS system, update the lastVoAllocatedDate to keep this synced.
    if (syncDto.adjustmentReasonCode == AdjustmentReasonCode.IEP && syncDto.changeLogSource == ChangeLogSource.SYSTEM) {
      LOG.info("Nomis Sync reason due to IEP allocation from system, adding /update allocation date for prisoner ${syncDto.prisonerId }in our service")
      prisonerDetailsService.updateVoLastCreatedDateOrCreatePrisoner(syncDto.prisonerId, syncDto.createdDate)
    }

    changeLogService.logSyncChange(syncDto)
  }

  private fun processSync(prisonerId: String, prisonerDpsBalance: Int, balanceChange: Int, visitOrderType: VisitOrderType, negativeVoType: NegativeVisitOrderType) {
    LOG.info("Entered process Sync - $visitOrderType / $negativeVoType - for prisoner $prisonerId, with a DPS balance of $prisonerDpsBalance, and a change of $balanceChange")
    when {
      prisonerDpsBalance > 0 -> {
        handlePositiveBalance(prisonerId, prisonerDpsBalance, balanceChange, visitOrderType, negativeVoType)
      }
      prisonerDpsBalance < 0 -> {
        handleNegativeBalance(prisonerId, prisonerDpsBalance, balanceChange, visitOrderType, negativeVoType)
      }
      else -> {
        handleZeroBalance(prisonerId, balanceChange, visitOrderType, negativeVoType)
      }
    }
  }

  private fun handlePositiveBalance(prisonerId: String, prisonerDpsBalance: Int, balanceChange: Int, visitOrderType: VisitOrderType, negativeVoType: NegativeVisitOrderType) {
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
        LOG.info("Balance decreased and is negative for prisoner $prisonerId, expiring all $visitOrderType and creating $negativeVosToCreate $negativeVoType")
        visitOrderRepository.expireVisitOrdersGivenAmount(prisonerId, visitOrderType, null)
        createAndSaveNegativeVisitOrders(prisonerId, negativeVoType, negativeVosToCreate)
      }
    }
  }

  private fun handleNegativeBalance(prisonerId: String, prisonerDpsBalance: Int, balanceChange: Int, visitOrderType: VisitOrderType, negativeVoType: NegativeVisitOrderType) {
    LOG.info("Negative DPS balance, syncing with nomis for prisoner $prisonerId")
    if (balanceChange <= 0) {
      LOG.info("Balance decreased and remains negative for prisoner $prisonerId, creating $balanceChange, $negativeVoType")
      createAndSaveNegativeVisitOrders(prisonerId, negativeVoType, abs(balanceChange))
    } else {
      if ((prisonerDpsBalance + balanceChange) <= 0) {
        LOG.info("Balance increased but remains negative for prisoner $prisonerId, repaying $balanceChange, $negativeVoType")
        negativeVisitOrderRepository.repayVisitOrdersGivenAmount(prisonerId, negativeVoType, abs(balanceChange).toLong())
      } else {
        val positiveVosToCreate = prisonerDpsBalance + balanceChange
        LOG.info("Balance increased and is positive for prisoner $prisonerId, repaying all $negativeVoType and creating $positiveVosToCreate $visitOrderType")
        negativeVisitOrderRepository.repayVisitOrdersGivenAmount(prisonerId, negativeVoType, null)
        createAndSaveVisitOrders(prisonerId, visitOrderType, positiveVosToCreate)
      }
    }
  }

  private fun handleZeroBalance(prisonerId: String, balanceChange: Int, visitOrderType: VisitOrderType, negativeVoType: NegativeVisitOrderType) {
    LOG.info("Zero DPS balance, syncing with nomis for prisoner $prisonerId")
    if (balanceChange >= 0) {
      LOG.info("Balance increased for prisoner $prisonerId, creating $balanceChange $visitOrderType")
      createAndSaveVisitOrders(prisonerId, visitOrderType, balanceChange)
    } else {
      LOG.info("Balance decreased for prisoner $prisonerId, creating $balanceChange $negativeVoType")
      createAndSaveNegativeVisitOrders(prisonerId, negativeVoType, abs(balanceChange))
    }
  }

  private fun createAndSaveVisitOrders(prisonerId: String, visitOrderType: VisitOrderType, amountToCreate: Int) {
    val visitOrders = mutableListOf<VisitOrder>()
    repeat(amountToCreate) {
      visitOrders.add(
        VisitOrder(
          prisonerId = prisonerId,
          type = visitOrderType,
          status = VisitOrderStatus.AVAILABLE,
        ),
      )
    }
    visitOrderRepository.saveAll(visitOrders)
  }

  private fun createAndSaveNegativeVisitOrders(prisonerId: String, negativeVoType: NegativeVisitOrderType, amountToCreate: Int) {
    val negativeVisitOrders = mutableListOf<NegativeVisitOrder>()
    repeat(amountToCreate) {
      negativeVisitOrders.add(
        NegativeVisitOrder(
          prisonerId = prisonerId,
          type = negativeVoType,
          status = NegativeVisitOrderStatus.USED,
        ),
      )
    }
    negativeVisitOrderRepository.saveAll(negativeVisitOrders)
  }

  private fun compareBalanceBeforeSync(syncDto: VisitAllocationPrisonerSyncDto, prisonerBalance: PrisonerBalanceDto) {
    if (prisonerBalance.voBalance != syncDto.oldVoBalance || prisonerBalance.pvoBalance != syncDto.oldPvoBalance) {
      LOG.error("Discovered discrepancy between NOMIS and DPS balances. Logging error to application insights for prisoner ${syncDto.prisonerId}")
      val telemetryBalanceProperties = mapOf(
        "prisonerId" to syncDto.prisonerId,
        "nomisVoBalance" to syncDto.oldVoBalance.toString(),
        "nomisPvoBalance" to syncDto.oldPvoBalance.toString(),
        "dpsVoBalance" to prisonerBalance.voBalance.toString(),
        "dpsPvoBalance" to prisonerBalance.pvoBalance.toString(),
      )
      telemetryService.trackEvent(TelemetryEventType.BALANCES_OUT_OF_SYNC, telemetryBalanceProperties)
    }
  }

  private fun calculateAmountToChange(dpsBalance: Int, oldNomisBalance: Int, changeToNomisBalance: Int): Int = (oldNomisBalance + changeToNomisBalance) - dpsBalance
}
