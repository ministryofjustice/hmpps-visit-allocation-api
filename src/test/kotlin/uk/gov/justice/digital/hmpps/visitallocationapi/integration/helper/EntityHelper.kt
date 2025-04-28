package uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderPrison
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.ChangeLogRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.PrisonerDetailsRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderPrisonRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import java.time.LocalDateTime

@Component
@Transactional
class EntityHelper(
  val visitOrderPrisonRepository: VisitOrderPrisonRepository,
  val visitOrderRepository: VisitOrderRepository,
  val negativeVisitOrderRepository: NegativeVisitOrderRepository,
  val changeLogRepository: ChangeLogRepository,
  private val prisonerDetailsRepository: PrisonerDetailsRepository,
  private val prisonerDetailsService: PrisonerDetailsService,
) {

  @Transactional
  fun savePrison(visitOrderPrison: VisitOrderPrison) {
    visitOrderPrisonRepository.saveAndFlush(visitOrderPrison)
  }

  @Transactional
  fun createPrisonerDetails(prisoner: PrisonerDetails): PrisonerDetails = prisonerDetailsRepository.saveAndFlush(prisoner)

  @Transactional
  fun createAndSaveVisitOrders(prisonerId: String, visitOrderType: VisitOrderType, status: VisitOrderStatus, createdDateTime: LocalDateTime, amountToCreate: Int) {
    val prisoner = prisonerDetailsRepository.findByPrisonerId(prisonerId)!!

    val visitOrders = mutableListOf<VisitOrder>()
    repeat(amountToCreate) {
      visitOrders.add(
        VisitOrder(
          prisonerId = prisoner.prisonerId,
          type = visitOrderType,
          status = status,
          prisoner = prisoner,
          createdTimestamp = createdDateTime,
        ),
      )
    }

    prisoner.visitOrders.addAll(visitOrders)
    prisonerDetailsRepository.saveAndFlush(prisoner)
  }

  @Transactional
  fun createAndSaveNegativeVisitOrders(prisonerId: String, negativeVoType: VisitOrderType, amountToCreate: Int) {
    val prisoner = prisonerDetailsRepository.findByPrisonerId(prisonerId)!!

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

    prisoner.negativeVisitOrders.addAll(negativeVisitOrders)
    prisonerDetailsRepository.saveAndFlush(prisoner)
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
