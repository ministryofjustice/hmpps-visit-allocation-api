package uk.gov.justice.digital.hmpps.visitallocationapi.integration

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.service.PrisonerDetailsService
import java.time.LocalDate

@DisplayName("PrisonerDetailsRetrievalTest - Expired VOs excluded")
class PrisonerDetailsRetrievalTest : IntegrationTestBase() {

  @Autowired
  private lateinit var service: PrisonerDetailsService

  @Autowired
  private lateinit var em: EntityManager

  companion object {
    const val PRISONER_ID = "AA123456"
  }

  @Test
  @Transactional
  fun `When prisoner details are loaded, then expired VOs and PVOs are excluded from the prisoners visitOrders list`() {
    // Given
    val prisoner = PrisonerDetails(prisonerId = PRISONER_ID, lastVoAllocatedDate = LocalDate.now().minusDays(14), null)
    prisoner.visitOrders.add(VisitOrder(type = VisitOrderType.VO, status = VisitOrderStatus.EXPIRED, prisoner = prisoner))
    prisoner.visitOrders.add(VisitOrder(type = VisitOrderType.VO, status = VisitOrderStatus.AVAILABLE, prisoner = prisoner))
    prisoner.visitOrders.add(VisitOrder(type = VisitOrderType.PVO, status = VisitOrderStatus.EXPIRED, prisoner = prisoner))
    prisoner.visitOrders.add(VisitOrder(type = VisitOrderType.PVO, status = VisitOrderStatus.AVAILABLE, prisoner = prisoner))

    em.persist(prisoner)
    em.flush()
    em.clear()

    // When
    val prisonerDetails = service.getPrisonerDetails(PRISONER_ID)!!

    // Then
    assertThat(prisonerDetails.visitOrders.size).isEqualTo(2)
    assertThat(prisonerDetails.visitOrders.any { it.status == VisitOrderStatus.EXPIRED }).isFalse
  }
}
