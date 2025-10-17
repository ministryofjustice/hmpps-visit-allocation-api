package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.clients.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.admin.PrisonNegativeBalanceCountDto
import uk.gov.justice.digital.hmpps.visitallocationapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository

@Service
class AdminService(
  private val prisonerSearchClient: PrisonerSearchClient,
  private val processPrisonerService: ProcessPrisonerService,
  private val changeLogService: ChangeLogService,
  private val snsService: SnsService,
  private val negativeVisitOrderRepository: NegativeVisitOrderRepository,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
  fun resetPrisonerNegativeBalance(prisonCode: String) {
    LOG.info("Entered AdminService - resetPrisonerNegativeBalance for prison {}", prisonCode)

    val prisoners = prisonerSearchClient.getAllPrisonersByPrisonId(prisonCode).content
    if (prisoners.isEmpty()) {
      LOG.info("No prisoners found for prison $prisonCode")
      return
    }

    LOG.info("Found ${prisoners.size} prisoners for prison $prisonCode")
    for (prisoner in prisoners) {
      val changeLogReference = processPrisonerService.processAdminResetPrisonerNegativeBalance(prisoner.prisonerId)
      if (changeLogReference != null) {
        val changeLog = changeLogService.findChangeLogForPrisonerByReference(prisoner.prisonerId, changeLogReference)
        if (changeLog != null) {
          snsService.sendPrisonAllocationAdjustmentCreatedEvent(changeLog)
        }
      }
    }
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
  fun getPrisonPrisonerNegativeBalanceCount(prisonCode: String): PrisonNegativeBalanceCountDto {
    LOG.info("Entered AdminService - getPrisonPrisonerNegativeBalanceCount for prison {}", prisonCode)

    val prisoners = prisonerSearchClient.getAllPrisonersByPrisonId(prisonCode).content
    if (prisoners.isEmpty()) {
      LOG.info("No prisoners found for prison $prisonCode")
      throw NotFoundException("AdminService getPrisonPrisonerNegativeBalanceCount failed to find any prisoners for prison $prisonCode")
    }

    val count = negativeVisitOrderRepository.countPrisonersWithNegativeVisitOrderBalance(prisoners.map { it.prisonerId })
    LOG.info("Found $count prisoners with negative balance for prison $prisonCode")
    return PrisonNegativeBalanceCountDto(prisonCode = prisonCode, count = count)
  }
}
