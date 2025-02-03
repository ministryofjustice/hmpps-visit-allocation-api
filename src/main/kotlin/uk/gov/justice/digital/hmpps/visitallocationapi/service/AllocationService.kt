package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.IncentivesClient
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.incentives.PrisonIncentiveAmountsDto
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.prisoner.search.PrisonerDto
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
  suspend fun processPrisonerAllocation(prisonerId: String, prisonerDto: PrisonerDto? = null, allPrisonIncentiveAmounts: List<PrisonIncentiveAmountsDto>? = null) {
    LOG.info("Entered AllocationService - processPrisonerAllocation with prisonerId $prisonerId")

    val prisoner = prisonerDto ?: prisonerSearchClient.getPrisonerById(prisonerId)
    val prisonerIncentive = incentivesClient.getPrisonerIncentiveReviewHistory(prisoner.prisonerId)
    val prisonIncentiveAmounts = allPrisonIncentiveAmounts?.first { it.levelCode == prisonerIncentive.iepCode }
      ?: incentivesClient.getPrisonIncentiveLevelByLevelCode(prisoner.prisonId, prisonerIncentive.iepCode)

    val visitOrders = mutableListOf<VisitOrder>()

    // add VOs
    visitOrders.addAll(generateVos(prisoner, prisonIncentiveAmounts))

    // add PVOs
    visitOrders.addAll(generatePVos(prisoner, prisonIncentiveAmounts))

    visitOrderRepository.saveAll(visitOrders)

    LOG.info(
      "Successfully generated ${visitOrders.size} visit orders for prisoner prisoner $prisonerId: " + "${visitOrders.count { it.type == VisitOrderType.PVO }} PVOs and ${visitOrders.count { it.type == VisitOrderType.VO }} VOs",
    )
  }

  @Transactional
  suspend fun processPrisonAllocation(prisonId: String) {
    LOG.info("Entered AllocationService - processPrisonAllocation with prisonCode: $prisonId")

    val allPrisoners = prisonerSearchClient.getConvictedPrisonersByPrisonId(prisonId)
    val allIncentiveLevels = incentivesClient.getPrisonIncentiveLevels(prisonId)

    for (prisoner in allPrisoners) {
      processPrisonerAllocation(prisoner.prisonerId, prisoner, allIncentiveLevels)
    }

    LOG.info("Finished AllocationService - processPrisonAllocation with prisonCode: $prisonId, total records processed : ${allPrisoners.size}")
  }

  private fun createVisitOrder(prisonerId: String, type: VisitOrderType): VisitOrder {
    return VisitOrder(
      prisonerId = prisonerId,
      type = type,
      status = VisitOrderStatus.AVAILABLE,
      createdDate = LocalDate.now(),
      expiryDate = null,
    )
  }

  private fun isDueVO(prisonerId: String): Boolean {
    val lastVODate = visitOrderRepository.findLastAllocatedDate(prisonerId, VisitOrderType.VO)
    return lastVODate == null || lastVODate <= LocalDate.now().minusDays(14)
  }

  private fun isDuePVO(prisonerId: String): Boolean {
    val lastPVODate = visitOrderRepository.findLastAllocatedDate(prisonerId, VisitOrderType.PVO)

    // If they haven't been given a PVO before, we wait until their VO due date to allocate it.
    if (lastPVODate == null) {
      return isDueVO(prisonerId)
    }

    return lastPVODate <= LocalDate.now().minusDays(28)
  }

  private fun generateVos(prisoner: PrisonerDto, prisonIncentivesForPrisonerLevel: PrisonIncentiveAmountsDto): List<VisitOrder> {
    // Generate VOs
    val visitOrders = mutableListOf<VisitOrder>()
    if (isDueVO(prisoner.prisonerId)) {
      repeat(prisonIncentivesForPrisonerLevel.visitOrders) {
        visitOrders.add(createVisitOrder(prisoner.prisonerId, VisitOrderType.VO))
      }
    }

    return visitOrders.toList()
  }

  private fun generatePVos(prisoner: PrisonerDto, prisonIncentivesForPrisonerLevel: PrisonIncentiveAmountsDto): List<VisitOrder> {
    // Generate PVOs
    val visitOrders = mutableListOf<VisitOrder>()

    if (prisonIncentivesForPrisonerLevel.privilegedVisitOrders != 0) {
      if (isDuePVO(prisoner.prisonerId)) {
        repeat(prisonIncentivesForPrisonerLevel.privilegedVisitOrders) {
          visitOrders.add(createVisitOrder(prisoner.prisonerId, VisitOrderType.PVO))
        }
      }
    }
    return visitOrders.toList()
  }
}
