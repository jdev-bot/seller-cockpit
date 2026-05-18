package com.sellercockpit.api.repository

import com.sellercockpit.api.model.ProductCaseEntity
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class ProductCaseRepository : PanacheRepositoryBase<ProductCaseEntity, UUID> {
    fun findByUserId(userId: UUID) = find("userId", userId).list()
}
