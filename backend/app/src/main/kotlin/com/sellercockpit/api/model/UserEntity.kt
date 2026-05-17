package com.sellercockpit.api.model

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "sc_users")
class UserEntity : PanacheEntityBase {
    companion object : PanacheCompanion<UserEntity>

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    lateinit var id: java.util.UUID

    @Column(name = "firebase_uid", nullable = false, unique = true, length = 128)
    lateinit var firebaseUid: String

    @Column(name = "email", length = 256)
    var email: String? = null

    @Column(name = "display_name", length = 256)
    var displayName: String? = null

    @Column(name = "photo_url", length = 1024)
    var photoUrl: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
