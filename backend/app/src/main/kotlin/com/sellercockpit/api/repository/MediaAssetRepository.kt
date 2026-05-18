package com.sellercockpit.api.repository

import com.sellercockpit.api.model.MediaAssetEntity
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class MediaAssetRepository : PanacheRepositoryBase<MediaAssetEntity, UUID> {
    fun findByProductCaseId(productCaseId: UUID) = find("productCaseId", productCaseId).list()
}
