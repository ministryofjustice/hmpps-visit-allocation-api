package uk.gov.justice.digital.hmpps.visitallocationapi.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitallocationapi.dto.nomis.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.NegativeVisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderStatus
import uk.gov.justice.digital.hmpps.visitallocationapi.enums.VisitOrderType
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.NegativeVisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.model.entity.VisitOrder
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.NegativeVisitOrderRepository
import uk.gov.justice.digital.hmpps.visitallocationapi.repository.VisitOrderRepository
import java.time.LocalDate
import kotlin.math.abs

@Service
class NomisSyncService(
  private val visitOrderRepository: VisitOrderRepository,
  private val negativeVisitOrderRepository: NegativeVisitOrderRepository,
  private val changeLogService: ChangeLogService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun migratePrisoner(migrationDto: VisitAllocationPrisonerMigrationDto) {
    LOG.info("Entered NomisSyncService - migratePrisoner with migration dto {}", migrationDto)

    migrateBalance(migrationDto, VisitOrderType.VO)
    migrateBalance(migrationDto, VisitOrderType.PVO)

    changeLogService.logMigrationChange(migrationDto)

    LOG.info("Finished NomisSyncService - migratePrisoner successfully")
  }

  private fun migrateBalance(migrationDto: VisitAllocationPrisonerMigrationDto, type: VisitOrderType) {
    val balance = if (type == VisitOrderType.VO) {
      migrationDto.voBalance
    } else {
      migrationDto.pvoBalance
    }

    when {
      balance > 0 -> {
        LOG.info("Migrating prisoner ${migrationDto.prisonerId} with a ${type.name} balance of $balance")
        val visitOrders = List(balance) {
          VisitOrder(
            prisonerId = migrationDto.prisonerId,
            type = type,
            status = VisitOrderStatus.AVAILABLE,
            // TODO: VB-5220 - Should the PVO date be the VO date - 14 days in some instances? (To help with PVO generation spread).
            createdDate = migrationDto.lastVoAllocationDate,
            expiryDate = null,
          )
        }
        visitOrderRepository.saveAll(visitOrders)
      }
      balance < 0 -> {
        LOG.info("Migrating prisoner ${migrationDto.prisonerId} with a negative ${type.name} balance of $balance")
        val negativeVisitOrders = List(abs(balance)) {
          NegativeVisitOrder(
            prisonerId = migrationDto.prisonerId,
            status = NegativeVisitOrderStatus.USED,
            type = if (type == VisitOrderType.VO) {
              NegativeVisitOrderType.NEGATIVE_VO
            } else {
              NegativeVisitOrderType.NEGATIVE_PVO
            },
            createdDate = LocalDate.now(),
          )
        }
        negativeVisitOrderRepository.saveAll(negativeVisitOrders)
      }
      else -> {
        LOG.info("Not migrating ${type.name} balance for prisoner ${migrationDto.prisonerId} as it's 0")
      }
    }
  }
}
