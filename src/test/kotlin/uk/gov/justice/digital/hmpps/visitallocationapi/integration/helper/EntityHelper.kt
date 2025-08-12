package uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.ChangeLog
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.ChangeLogRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.PrisonerDetailsRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import java.time.LocalDateTime

@Transactional
@Component
class EntityHelper(
  val visitOrderRepository: VisitOrderRepository,
  val negativeVisitOrderRepository: NegativeVisitOrderRepository,
  val changeLogRepository: ChangeLogRepository,
  private val prisonerDetailsRepository: PrisonerDetailsRepository,
) {

  fun createPrisonerDetails(prisoner: PrisonerDetails): PrisonerDetails = prisonerDetailsRepository.saveAndFlush(prisoner)

  fun createChangeLog(changeLog: ChangeLog): ChangeLog = changeLogRepository.save(changeLog)

  fun createAndSaveVisitOrders(prisonerId: String, visitOrderType: VisitOrderType, status: VisitOrderStatus, createdDateTime: LocalDateTime, amountToCreate: Int) {
    val prisoner = prisonerDetailsRepository.findById(prisonerId).get()

    val visitOrders = mutableListOf<VisitOrder>()
    repeat(amountToCreate) {
      visitOrders.add(
        VisitOrder(
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

  fun createAndSaveNegativeVisitOrders(prisonerId: String, negativeVoType: VisitOrderType, amountToCreate: Int) {
    val prisoner = prisonerDetailsRepository.findById(prisonerId).get()

    val negativeVisitOrders = mutableListOf<NegativeVisitOrder>()
    repeat(amountToCreate) {
      negativeVisitOrders.add(
        NegativeVisitOrder(
          type = negativeVoType,
          status = NegativeVisitOrderStatus.USED,
          prisoner = prisoner,
        ),
      )
    }

    prisoner.negativeVisitOrders.addAll(negativeVisitOrders)
    prisonerDetailsRepository.saveAndFlush(prisoner)
  }

  fun deleteAll() {
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
