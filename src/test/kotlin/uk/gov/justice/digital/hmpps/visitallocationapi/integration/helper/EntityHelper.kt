package uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderPrison
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.ChangeLogRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.PrisonerDetailsRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderPrisonRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository

@Component
@Transactional
class EntityHelper(
  val visitOrderPrisonRepository: VisitOrderPrisonRepository,
  val visitOrderRepository: VisitOrderRepository,
  val negativeVisitOrderRepository: NegativeVisitOrderRepository,
  val changeLogRepository: ChangeLogRepository,
  private val prisonerDetailsRepository: PrisonerDetailsRepository,
) {

  @Transactional
  fun savePrison(visitOrderPrison: VisitOrderPrison) {
    visitOrderPrisonRepository.saveAndFlush(visitOrderPrison)
  }

  @Transactional
  fun createAndSaveVisitOrders(prisonerId: String, visitOrderType: VisitOrderType, amountToCreate: Int) {
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

  @Transactional
  fun createAndSaveNegativeVisitOrders(prisonerId: String, negativeVoType: NegativeVisitOrderType, amountToCreate: Int) {
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

  @Transactional
  fun deleteAll() {
    visitOrderPrisonRepository.deleteAll()
    visitOrderPrisonRepository.flush()

    visitOrderRepository.deleteAll()
    visitOrderRepository.flush()

    negativeVisitOrderRepository.deleteAll()
    negativeVisitOrderRepository.flush()

    changeLogRepository.deleteAll()
    changeLogRepository.flush()

    prisonerDetailsRepository.deleteAll()
    prisonerDetailsRepository.flush()
  }
}
