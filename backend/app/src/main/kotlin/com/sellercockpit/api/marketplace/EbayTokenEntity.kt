package com.sellercockpit.api.marketplace

import com.sellercockpit.domain.model.*
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "ebay_tokens")
open class EbayTokenEntity : PanacheEntityBase {
    companion object : PanacheCompanion<EbayTokenEntity> {
        fun findValidByUser(userId: UUID) =
            find("userId = ?1 and expiresAt > ?2", userId, Instant.now()).firstResult()
    }

    @Id
    @Column(name = "id")
    open lateinit var id: UUID

    @Column(name = "user_id", nullable = false)
    open lateinit var userId: UUID

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    open lateinit var accessToken: String

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    open var refreshToken: String? = null

    @Column(name = "expires_at", nullable = false)
    open lateinit var expiresAt: Instant

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now()
}
