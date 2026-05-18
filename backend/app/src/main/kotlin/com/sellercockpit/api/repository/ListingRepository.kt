package com.sellercockpit.api.repository

import com.sellercockpit.api.model.ListingDraftEntity
import com.sellercockpit.api.model.MarketplaceListingEntity
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class ListingDraftRepository : PanacheRepositoryBase<ListingDraftEntity, UUID> {
    fun findByProductCaseId(productCaseId: UUID) = find("productCaseId", productCaseId).list()
}

@ApplicationScoped
class MarketplaceListingRepository : PanacheRepositoryBase<MarketplaceListingEntity, UUID> {
    fun findByProductCaseId(productCaseId: UUID) = find("productCaseId", productCaseId).list()
}
