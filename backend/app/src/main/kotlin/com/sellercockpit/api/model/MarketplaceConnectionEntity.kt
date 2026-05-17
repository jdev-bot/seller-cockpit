package com.sellercockpit.api.model

import com.sellercockpit.domain.model.*
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "marketplace_connections")
open class MarketplaceConnectionEntity : PanacheEntityBase {
    companion object : PanacheCompanion<MarketplaceConnectionEntity> {
        fun findByUserAndPlatform(userId: UUID, platform: MarketplacePlatform) =
            find("userId = ?1 and platform = ?2", userId, platform).firstResult()
    }

    @Id
    @Column(name = "id")
    open lateinit var id: UUID

    @Column(name = "user_id", nullable = false)
    open lateinit var userId: UUID

    @Column(name = "platform", nullable = false)
    @Enumerated(EnumType.STRING)
    open lateinit var platform: MarketplacePlatform

    @Column(name = "account_id")
    open var accountId: String? = null

    @Column(name = "account_name")
    open var accountName: String? = null

    @Column(name = "access_token_encrypted", columnDefinition = "TEXT")
    open var accessTokenEncrypted: String? = null

    @Column(name = "refresh_token_encrypted", columnDefinition = "TEXT")
    open var refreshTokenEncrypted: String? = null

    @Column(name = "token_expires_at")
    open var tokenExpiresAt: Instant? = null

    @Column(name = "is_active", nullable = false)
    open var isActive: Boolean = true

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now()
}
