package com.sellercockpit.api.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
open class UserEntity(
    @Id
    @Column(name = "id")
    open val id: UUID = UUID.randomUUID(),

    @Column(name = "email", nullable = false, unique = true)
    open var email: String = "",

    @Column(name = "display_name")
    open var displayName: String? = null,

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now()
)
