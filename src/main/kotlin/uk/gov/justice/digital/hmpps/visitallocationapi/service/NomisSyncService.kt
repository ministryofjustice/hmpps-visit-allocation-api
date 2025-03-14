package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.AdjustmentReasonCode
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.nomis.ChangeLogSource
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import kotlin.math.abs

// TODO: VB-5234 - Nomis Sync Ticket. Continue with comments below.

@Service
class NomisSyncService(
  val balanceService: BalanceService,
  val prisonerDetailsService: PrisonerDetailsService,
  val visitOrderRepository: VisitOrderRepository,
  val negativeVisitOrderRepository: NegativeVisitOrderRepository,
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
    if (syncDto.changeToVoBalance != null) {
      processSync(syncDto.prisonerId, prisonerBalance.voBalance, syncDto.changeToVoBalance, VisitOrderType.VO, NegativeVisitOrderType.NEGATIVE_VO)
    }

    // If PVO balance has changed, sync it
    if (syncDto.changeToPvoBalance != null) {
      processSync(syncDto.prisonerId, prisonerBalance.pvoBalance, syncDto.changeToPvoBalance, VisitOrderType.PVO, NegativeVisitOrderType.NEGATIVE_PVO)
    }

    // If this is a VO allocation from the NOMIS system, update the lastVoAllocatedDate to keep this synced.
    if (syncDto.adjustmentReasonCode == AdjustmentReasonCode.IEP && syncDto.changeLogSource == ChangeLogSource.SYSTEM) {
      prisonerDetailsService.updateVoLastCreatedDateOrCreatePrisoner(syncDto.prisonerId, syncDto.createdDate)
    }
  }

  private fun processSync(prisonerId: String, prisonerDpsBalance: Int, changeToNomisBalance: Int, visitOrderType: VisitOrderType, negativeVoType: NegativeVisitOrderType) {
    when {
      prisonerDpsBalance > 0 -> {
        handlePositiveBalance(prisonerId, prisonerDpsBalance, changeToNomisBalance, visitOrderType, negativeVoType)
      }
      prisonerDpsBalance < 0 -> {
        handleNegativeBalance(prisonerId, prisonerDpsBalance, changeToNomisBalance, visitOrderType, negativeVoType)
      }
      else -> {
        handleZeroBalance(prisonerId, changeToNomisBalance, visitOrderType, negativeVoType)
      }
    }
  }

  private fun handlePositiveBalance(prisonerId: String, prisonerDpsBalance: Int, changeToNomisBalance: Int, visitOrderType: VisitOrderType, negativeVoType: NegativeVisitOrderType) {
    if (changeToNomisBalance >= 0) {
      createAndSaveVisitOrders(prisonerId, visitOrderType, changeToNomisBalance)
    } else {
      if ((prisonerDpsBalance - changeToNomisBalance) >= 0) {
        // TODO: Should we be expiring AVAILABLE first then ACCUMULATED, or does it not matter?
        visitOrderRepository.expireVisitOrdersGivenAmount(prisonerId, visitOrderType, changeToNomisBalance)
      } else {
        val negativeVosToCreate = abs(prisonerDpsBalance - changeToNomisBalance)
        visitOrderRepository.expireAllVisitOrders(prisonerId, visitOrderType)
        createAndSaveNegativeVisitOrders(prisonerId, negativeVoType, negativeVosToCreate)
      }
    }
  }

  private fun handleNegativeBalance(prisonerId: String, prisonerDpsBalance: Int, changeToNomisBalance: Int, visitOrderType: VisitOrderType, negativeVoType: NegativeVisitOrderType) {
    if (changeToNomisBalance <= 0) {
      createAndSaveNegativeVisitOrders(prisonerId, negativeVoType, changeToNomisBalance)
    } else {
      if ((prisonerDpsBalance + changeToNomisBalance) <= 0) {
        negativeVisitOrderRepository.repayVisitOrdersGivenAmount(prisonerId, negativeVoType, changeToNomisBalance)
      } else {
        val positiveVosToCreate = prisonerDpsBalance + changeToNomisBalance
        negativeVisitOrderRepository.repayAllVisitOrders(prisonerId, negativeVoType)
        createAndSaveVisitOrders(prisonerId, visitOrderType, positiveVosToCreate)
      }
    }
  }

  private fun handleZeroBalance(prisonerId: String, changeToNomisBalance: Int, visitOrderType: VisitOrderType, negativeVoType: NegativeVisitOrderType) {
    if (changeToNomisBalance >= 0) {
      createAndSaveVisitOrders(prisonerId, visitOrderType, changeToNomisBalance)
    } else {
      createAndSaveNegativeVisitOrders(prisonerId, negativeVoType, changeToNomisBalance)
    }
  }

  private fun createAndSaveVisitOrders(prisonerId: String, visitOrderType: VisitOrderType, amountToCreate: Int) {
    val visitOrders = mutableListOf<VisitOrder>()
    repeat(amountToCreate) {
      visitOrders.add(
        VisitOrder(
          prisonerId = prisonerId,
          type = visitOrderType,
          status = VisitOrderStatus.AVAILABLE
        )
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
          status = NegativeVisitOrderStatus.USED
        )
      )
    }
    negativeVisitOrderRepository.saveAll(negativeVisitOrders)
  }

  private fun compareBalanceBeforeSync(syncDto: VisitAllocationPrisonerSyncDto, prisonerBalance: PrisonerBalanceDto) {
    // Compare if the balance we hold matches the "before balance" from nomis.
    if (prisonerBalance.voBalance != syncDto.oldVoBalance || prisonerBalance.pvoBalance != syncDto.oldPvoBalance) {
      TODO("Add call to telemetry service to log this anomaly. This will currently trigger for new prisoners.")
    }
  }
}
