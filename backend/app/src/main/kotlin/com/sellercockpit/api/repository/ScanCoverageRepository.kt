package com.sellercockpit.api.repository

import com.sellercockpit.api.model.ScanCoverageEntity
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class ScanCoverageRepository : PanacheRepository<ScanCoverageEntity> {
    companion object : PanacheCompanion<ScanCoverageEntity> {
        fun findByProductCaseId(productCaseId: UUID) =
            find("productCaseId", productCaseId).firstResult()
    }
}
