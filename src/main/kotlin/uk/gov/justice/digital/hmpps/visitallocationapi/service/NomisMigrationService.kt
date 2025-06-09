package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.PrisonerDetails
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

@Service
class NomisMigrationService(
  private val changeLogService: ChangeLogService,
  private val prisonerDetailsService: PrisonerDetailsService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val NULL_LAST_ALLOCATION_DATE_OFFSET = 28L
  }

  @Transactional
  fun migratePrisoner(migrationDto: VisitAllocationPrisonerMigrationDto) {
    LOG.info("Entered NomisMigrationService - migratePrisoner with migration dto {}", migrationDto)

    // If prisoner exists, reset their details and balance ready for migration
    if (prisonerDetailsService.getPrisonerDetails(migrationDto.prisonerId) != null) {
      LOG.info("Prisoner ${migrationDto.prisonerId} found in DB, resetting their balance ready for migration")
      prisonerDetailsService.removePrisonerDetails(migrationDto.prisonerId)
    }

    // Due to bad data in NOMIS, it's possible for a prisoner to exist with a balance but no IEP date.
    // When this happens, set it to TODAY - 28 DAYS. This allows prisoner to receive IEP allocation on our side ASAP.
    if (migrationDto.lastVoAllocationDate == null) {
      migrationDto.lastVoAllocationDate = LocalDate.now().minusDays(NULL_LAST_ALLOCATION_DATE_OFFSET)
    }

    val dpsPrisoner = migratePrisonerDetails(migrationDto)
    migrateBalance(migrationDto, VisitOrderType.VO, dpsPrisoner)
    migrateBalance(migrationDto, VisitOrderType.PVO, dpsPrisoner)

    dpsPrisoner.changeLogs.add(changeLogService.createLogMigrationChange(migrationDto, dpsPrisoner))

    LOG.info("Finished NomisMigrationService - migratePrisoner ${migrationDto.prisonerId} successfully")
  }

  private fun migrateBalance(migrationDto: VisitAllocationPrisonerMigrationDto, type: VisitOrderType, prisoner: PrisonerDetails) {
    val balance = if (type == VisitOrderType.VO) {
      migrationDto.voBalance
    } else {
      migrationDto.pvoBalance
    }

    when {
      balance > 0 -> {
        prisoner.visitOrders.addAll(createPositiveVisitOrders(migrationDto, type, balance, prisoner))
      }
      balance < 0 -> {
        prisoner.negativeVisitOrders.addAll(createNegativeVisitOrders(migrationDto, type, balance, prisoner))
      }
      else -> {
        LOG.info("Not migrating ${type.name} balance for prisoner ${migrationDto.prisonerId} as it's 0")
      }
    }
  }

  private fun createPositiveVisitOrders(
    migrationDto: VisitAllocationPrisonerMigrationDto,
    type: VisitOrderType,
    balance: Int,
    prisoner: PrisonerDetails,
  ): List<VisitOrder> {
    LOG.info("Migrating prisoner ${migrationDto.prisonerId} with a ${type.name} balance of $balance")
    val visitOrders = List(balance) {
      VisitOrder(
        prisonerId = prisoner.prisonerId,
        type = type,
        status = VisitOrderStatus.AVAILABLE,
        createdTimestamp = migrationDto.lastVoAllocationDate!!.atStartOfDay(),
        expiryDate = null,
        prisoner = prisoner,
      )
    }
    return visitOrders
  }

  private fun createNegativeVisitOrders(
    migrationDto: VisitAllocationPrisonerMigrationDto,
    type: VisitOrderType,
    balance: Int,
    prisoner: PrisonerDetails,
  ): List<NegativeVisitOrder> {
    LOG.info("Migrating prisoner ${migrationDto.prisonerId} with a negative ${type.name} balance of $balance")
    val negativeVisitOrders = List(abs(balance)) {
      NegativeVisitOrder(
        prisonerId = prisoner.prisonerId,
        status = NegativeVisitOrderStatus.USED,
        type = type,
        createdTimestamp = LocalDateTime.now(),
        prisoner = prisoner,
      )
    }
    return negativeVisitOrders
  }

  private fun migratePrisonerDetails(migrationDto: VisitAllocationPrisonerMigrationDto): PrisonerDetails {
    LOG.info("Migrating prisoner ${migrationDto.prisonerId} details (last allocated date - ${migrationDto.lastVoAllocationDate})")

    val lastPvoAllocatedDate = if (migrationDto.pvoBalance != 0) {
      migrationDto.lastVoAllocationDate
    } else {
      null
    }

    return prisonerDetailsService.createPrisonerDetails(prisonerId = migrationDto.prisonerId, newLastAllocatedDate = migrationDto.lastVoAllocationDate!!, newLastPvoAllocatedDate = lastPvoAllocatedDate)
  }
}
