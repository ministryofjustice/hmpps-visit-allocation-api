package uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
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
