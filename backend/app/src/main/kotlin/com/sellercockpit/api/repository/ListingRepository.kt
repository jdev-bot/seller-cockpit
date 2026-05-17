package com.sellercockpit.api.repository

import com.sellercockpit.api.model.ListingDraftEntity
import com.sellercockpit.api.model.MarketplaceListingEntity
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class ListingDraftRepository : PanacheRepository<ListingDraftEntity> {
    fun findByProductCaseId(productCaseId: UUID) = find("productCaseId", productCaseId).list()
}

@ApplicationScoped
class MarketplaceListingRepository : PanacheRepository<MarketplaceListingEntity> {
    fun findByProductCaseId(productCaseId: UUID) = find("productCaseId", productCaseId).list()
}
