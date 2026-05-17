package com.sellercockpit.api.repository

import com.sellercockpit.api.model.ProductCaseEntity
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class ProductCaseRepository : PanacheRepository<ProductCaseEntity> {
    companion object : PanacheCompanion<ProductCaseEntity> {
        fun findByUserId(userId: UUID) = find("userId", userId).list()
    }
}
