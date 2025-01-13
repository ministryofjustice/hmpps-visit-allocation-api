package uk.gov.justice.digital.hmpps.visitallocationapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VisitAllocationApi

fun main(args: Array<String>) {
  runApplication<VisitAllocationApi>(*args)
}
