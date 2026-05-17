package com.sellercockpit.api.service

import com.sellercockpit.api.auth.AuthenticatedUser
import com.sellercockpit.api.model.UserEntity
import com.sellercockpit.domain.model.User
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant

@ApplicationScoped
class UserService {

    @Transactional
    fun findOrCreateUser(authUser: AuthenticatedUser): UserEntity {
        val existing = UserEntity.find("firebaseUid", authUser.firebaseUid).firstResult()
        if (existing != null) {
            existing.email = authUser.email
            existing.displayName = authUser.displayName
            existing.updatedAt = Instant.now()
            return existing
        }

        val newUser = UserEntity()
        newUser.firebaseUid = authUser.firebaseUid
        newUser.email = authUser.email
        newUser.displayName = authUser.displayName
        newUser.createdAt = Instant.now()
        newUser.updatedAt = Instant.now()
        newUser.persist()
        return newUser
    }

    fun toDomain(entity: UserEntity): User = User(
        id = entity.id,
        email = entity.email ?: "",
        displayName = entity.displayName,
        createdAt = entity.createdAt
    )
}
