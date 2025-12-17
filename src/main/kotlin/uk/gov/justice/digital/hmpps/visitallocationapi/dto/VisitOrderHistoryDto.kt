package uk.gov.justice.digital.hmpps.visitallocationapi.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderHistoryType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrderHistory
import java.time.LocalDateTime

data class VisitOrderHistoryDto(
  @param:Schema(description = "nomsNumber of the prisoner", example = "AA123456", required = true)
  val prisonerId: String,

  @param:Schema(description = "Visit Order History Type", example = "VO_ALLOCATION", required = true)
  val visitOrderHistoryType: VisitOrderHistoryType,

  @param:Schema(description = "Visit order history created data and time", example = "2018-12-01T13:45:00", required = true)
  val createdTimeStamp: LocalDateTime,

  @param:Schema(description = "VO balance after the visit order event", example = "5", required = true)
  val voBalance: Int,

  @param:Schema(description = "PVO balance after the visit order event", example = "5", required = true)
  val pvoBalance: Int,

  @param:Schema(description = "Username for who triggered the event, SYSTEM if system generated or STAFF username if STAFF event (e.g. manual adjustment)", example = "SYSTEM", required = true)
  val userName: String,

  @param:Schema(description = "Comment added by STAFF, null if SYSTEM event or if no comment was entered by STAFF", required = false)
  val comment: String? = null,

  @param:Schema(description = "Key, value combination of attributes", required = true)
  val attributes: List<VisitOrderHistoryAttributesDto>,
) {
  constructor(visitOrderHistory: VisitOrderHistory) : this(
    prisonerId = visitOrderHistory.prisoner.prisonerId,
    visitOrderHistoryType = visitOrderHistory.type,
    voBalance = visitOrderHistory.voBalance,
    pvoBalance = visitOrderHistory.pvoBalance,
    createdTimeStamp = visitOrderHistory.createdTimestamp,
    userName = visitOrderHistory.userName,
    comment = visitOrderHistory.comment,
    attributes = visitOrderHistory.visitOrderHistoryAttributes.map { VisitOrderHistoryAttributesDto(it) },
  )
}
