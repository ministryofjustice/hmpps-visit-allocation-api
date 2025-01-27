package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.IncentivesClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import java.time.LocalDate

@Service
class AllocationService(
  private val prisonerSearchClient: PrisonerSearchClient,
  private val incentivesClient: IncentivesClient,
  private val visitOrderRepository: VisitOrderRepository,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun startAllocation(prisonerId: String) {
    LOG.info("Entered AllocationService - startAllocation with prisonerId $prisonerId")
    visitOrderRepository.saveAll(generatePrisonerVoAndPvo(prisonerId))
  }

  private fun generatePrisonerVoAndPvo(prisonerId: String): List<VisitOrder> {
    val prisoner = prisonerSearchClient.getPrisonerById(prisonerId)
    val prisonerIncentive = incentivesClient.getPrisonerIncentiveReviewHistory(prisoner.prisonerNumber)
    val prisonIncentiveAmounts = incentivesClient.getPrisonIncentiveLevels(prisoner.prisonId!!, prisonerIncentive.iepCode)

    val visitOrders = mutableListOf<VisitOrder>()
    repeat(prisonIncentiveAmounts.visitOrders) {
      visitOrders.add(
        VisitOrder(
          prisonerId = prisonerId,
          type = VisitOrderType.VO,
          status = VisitOrderStatus.AVAILABLE,
          createdDate = LocalDate.now(),
          expiryDate = null,
        ),
      )
    }
    repeat(prisonIncentiveAmounts.privilegedVisitOrders) {
      visitOrders.add(
        VisitOrder(
          prisonerId = prisonerId,
          type = VisitOrderType.PVO,
          status = VisitOrderStatus.AVAILABLE,
          createdDate = LocalDate.now(),
          expiryDate = null,
        ),
      )
    }

    LOG.info("Successfully generated ${visitOrders.size} visit orders for prisonerId $prisonerId")

    return visitOrders
  }
}
