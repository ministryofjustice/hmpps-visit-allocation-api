package uk.gov.justice.digital.hmpps.visitallocationapi.integration.helper

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderPrison
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderPrisonRepository

@Component
@Transactional
class EntityHelper(
  val visitOrderPrisonRepository: VisitOrderPrisonRepository,
) {

  @Transactional
  fun savePrison(visitOrderPrison: VisitOrderPrison) {
    visitOrderPrisonRepository.saveAndFlush(visitOrderPrison)
  }

  @Transactional
  fun deleteAll() {
    visitOrderPrisonRepository.deleteAll()
    visitOrderPrisonRepository.flush()
  }
}
