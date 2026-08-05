package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.TelemetryEventType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VOBalancesUtil
import uk.gov.justice.digital.hmpps.visitallocationapi.utils.VisitOrdersUtil
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Transactional
@Service
class PrisonerMergeService(
  private val prisonerDetailsService: PrisonerDetailsService,
  private val changeLogService: ChangeLogService,
  private val telemetryClientService: TelemetryClientService,
  private val visitOrderHistoryService: VisitOrderHistoryService,
  private val voBalancesUtil: VOBalancesUtil,
  private val visitOrdersUtil: VisitOrdersUtil,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun processPrisonerMerge(newPrisonerId: String, removedPrisonerId: String): UUID? {
    var visitOrdersToBeCreated = 0
    var privilegedVisitOrdersToBeCreated = 0

    LOG.info("processPrisonerMerge with newPrisonerId - $newPrisonerId and removedPrisonerId - $removedPrisonerId")
    val newPrisonerDetails = prisonerDetailsService.getPrisonerDetailsWithLock(newPrisonerId)
      ?: prisonerDetailsService.createPrisonerDetails(newPrisonerId, LocalDate.now().minusDays(14), null)
    val newPrisonerVoBalance = voBalancesUtil.getPrisonerBalance(newPrisonerDetails)

    val removedPrisonerDetails = prisonerDetailsService.getPrisonerDetailsWithLock(removedPrisonerId)

    if (removedPrisonerDetails != null) {
      val removedPrisonerBalance = voBalancesUtil.getPrisonerBalance(removedPrisonerDetails)

      // create VOs - if the VO balance of the new prisoner is less than the removed prisoner's VO balance
      visitOrdersToBeCreated = removedPrisonerBalance.voBalance - newPrisonerVoBalance.voBalance
      if (visitOrdersToBeCreated > 0) {
        LOG.info("Creating $visitOrdersToBeCreated new VOs for prisoner - $newPrisonerId post merge with removed prisoner - $removedPrisonerId")
        repeat(visitOrdersToBeCreated) {
          val lastVoAllocatedDate = newPrisonerDetails.lastVoAllocatedDate
          newPrisonerDetails.visitOrders.add(visitOrdersUtil.createAvailableVisitOrder(newPrisonerDetails, VisitOrderType.VO, createdTimestamp = lastVoAllocatedDate.atStartOfDay()))
        }
      }

      // create PVOs - if the PVO balance of the new prisoner is less than the removed prisoner's PVO balance
      privilegedVisitOrdersToBeCreated = removedPrisonerBalance.pvoBalance - newPrisonerVoBalance.pvoBalance
      if (privilegedVisitOrdersToBeCreated > 0) {
        LOG.info("Creating $privilegedVisitOrdersToBeCreated new PVOs for prisoner - $newPrisonerId post merge with removed prisoner - $removedPrisonerId")
        repeat(privilegedVisitOrdersToBeCreated) {
          val createdTimestamp = newPrisonerDetails.lastPvoAllocatedDate?.atStartOfDay() ?: LocalDateTime.now()
          newPrisonerDetails.visitOrders.add(visitOrdersUtil.createAvailableVisitOrder(newPrisonerDetails, VisitOrderType.PVO, createdTimestamp = createdTimestamp))
        }
      }
    } else {
      LOG.info("Prisoner ID - $removedPrisonerId, removed as part of the merge does not exist on VO Allocation DB, no processing needed.")
    }

    return if (visitOrdersToBeCreated > 0 || privilegedVisitOrdersToBeCreated > 0) {
      visitOrderHistoryService.logAllocationForPrisonerMerge(dpsPrisoner = newPrisonerDetails, newPrisonerId = newPrisonerId, removedPrisonerId = removedPrisonerId)

      // add a changelog entry if new VO / PVOs have been added
      val changeLog = changeLogService.createLogAllocationForPrisonerMerge(
        dpsPrisoner = newPrisonerDetails,
        newPrisonerId = newPrisonerId,
        removedPrisonerId = removedPrisonerId,
      )
      newPrisonerDetails.changeLogs.add(changeLog)

      telemetryClientService.trackEvent(
        TelemetryEventType.VO_ADDED_POST_MERGE,
        mapOf(
          "prisonerId" to newPrisonerId,
          "removedPrisonerId" to removedPrisonerId,
          "voAddedPostMerge" to visitOrdersToBeCreated.toString(),
          "pvoAddedPostMerge" to privilegedVisitOrdersToBeCreated.toString(),
        ),
      )
      changeLog.reference
    } else {
      LOG.info("No VOs / PVOs were added post merge of prisonerId - $newPrisonerId and removedPrisonerId - $removedPrisonerId")
      null
    }
  }
}
