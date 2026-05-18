package com.sellercockpit.api.repository

import com.sellercockpit.api.model.ScanCoverageEntity
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class ScanCoverageRepository : PanacheRepositoryBase<ScanCoverageEntity, UUID> {
    fun findByProductCaseId(productCaseId: UUID) =
        find("productCaseId", productCaseId).firstResult()
}
