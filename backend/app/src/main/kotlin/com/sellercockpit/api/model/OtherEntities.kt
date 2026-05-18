package com.sellercockpit.api.model

import com.sellercockpit.domain.model.*
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "listing_drafts")
open class ListingDraftEntity : PanacheEntityBase {
    companion object : PanacheCompanion<ListingDraftEntity>

    @Id
    @Column(name = "id")
    open lateinit var id: UUID

    @Column(name = "product_case_id", nullable = false)
    open lateinit var productCaseId: UUID

    @Column(name = "platform", nullable = false)
    @Enumerated(EnumType.STRING)
    open lateinit var platform: MarketplacePlatform

    @Column(name = "title", nullable = false)
    open lateinit var title: String

    @Column(name = "description", nullable = false)
    open lateinit var description: String

    @Column(name = "category")
    open var category: String? = null

    @Column(name = "condition_text", nullable = false)
    open lateinit var conditionText: String

    @Column(name = "price_amount", nullable = false, precision = 19, scale = 4)
    open lateinit var priceAmount: BigDecimal

    @Column(name = "currency", nullable = false)
    open var currency: String = "EUR"

    @Column(name = "image_ids", columnDefinition = "jsonb")
    open var imageIds: List<String>? = null

    @Column(name = "attributes", columnDefinition = "jsonb")
    open var attributes: Map<String, String>? = null

    @Column(name = "warnings", columnDefinition = "jsonb")
    open var warnings: List<String>? = null

    @Column(name = "ready_to_publish", nullable = false)
    open var readyToPublish: Boolean = false

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now()
}

@Entity
@Table(name = "marketplace_listings")
open class MarketplaceListingEntity : PanacheEntityBase {
    companion object : PanacheCompanion<MarketplaceListingEntity>

    @Id
    @Column(name = "id")
    open lateinit var id: UUID

    @Column(name = "product_case_id", nullable = false)
    open lateinit var productCaseId: UUID

    @Column(name = "platform", nullable = false)
    @Enumerated(EnumType.STRING)
    open lateinit var platform: MarketplacePlatform

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    open lateinit var status: MarketplaceListingStatus

    @Column(name = "external_listing_id")
    open var externalListingId: String? = null

    @Column(name = "external_url")
    open var externalUrl: String? = null

    @Column(name = "title", nullable = false)
    open lateinit var title: String

    @Column(name = "description", nullable = false)
    open lateinit var description: String

    @Column(name = "price_amount", nullable = false, precision = 19, scale = 4)
    open lateinit var priceAmount: BigDecimal

    @Column(name = "currency", nullable = false)
    open var currency: String = "EUR"

    @Column(name = "published_at")
    open var publishedAt: Instant? = null

    @Column(name = "last_synced_at")
    open var lastSyncedAt: Instant? = null

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now()
}

@Entity
@Table(name = "media_assets")
open class MediaAssetEntity : PanacheEntityBase {
    companion object : PanacheCompanion<MediaAssetEntity>

    @Id
    @Column(name = "id")
    open lateinit var id: UUID

    @Column(name = "product_case_id", nullable = false)
    open lateinit var productCaseId: UUID

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    open lateinit var type: MediaAssetType

    @Column(name = "storage_url", nullable = false)
    open lateinit var storageUrl: String

    @Column(name = "thumbnail_url")
    open var thumbnailUrl: String? = null

    @Column(name = "selected_for_listing", nullable = false)
    open var selectedForListing: Boolean = false

    @Column(name = "sort_order")
    open var sortOrder: Int? = null

    @Column(name = "metadata", columnDefinition = "jsonb")
    open var metadata: Map<String, String>? = null

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now()
}

@Entity
@Table(name = "scan_coverages")
open class ScanCoverageEntity : PanacheEntityBase {
    companion object : PanacheCompanion<ScanCoverageEntity>

    @Id
    @Column(name = "id")
    open lateinit var id: UUID

    @Column(name = "product_case_id", nullable = false, unique = true)
    open lateinit var productCaseId: UUID

    @Column(name = "grid_rows", nullable = false)
    open var gridRows: Int = 4

    @Column(name = "grid_cols", nullable = false)
    open var gridCols: Int = 6

    @Column(name = "covered_cells", nullable = false)
    open var coveredCells: Int = 0

    @Column(name = "total_cells", nullable = false)
    open var totalCells: Int = 24

    @Column(name = "coverage_percent", nullable = false)
    open var coveragePercent: Int = 0

    @Column(name = "elapsed_ms", nullable = false)
    open var elapsedMs: Long = 0

    @Column(name = "is_complete", nullable = false)
    open var isComplete: Boolean = false

    @Column(name = "auto_stopped", nullable = false)
    open var autoStopped: Boolean = false

    @Column(name = "cell_data", columnDefinition = "jsonb")
    open var cellData: String = "[]"

    @Column(name = "missing_regions", columnDefinition = "jsonb")
    open var missingRegions: String = "[]"

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now()
}
