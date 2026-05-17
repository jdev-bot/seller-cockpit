package com.sellercockpit.api.auth

import java.security.Principal
import java.util.UUID

/**
 * Injects the current Firebase-authenticated user into resource methods.
 * Usage: @Context user: AuthenticatedUser
 */
data class AuthenticatedUser(
    val firebaseUid: String,
    val email: String? = null,
    val displayName: String? = null
) : Principal {
    fun toUserId(): UUID = UUID.nameUUIDFromBytes(firebaseUid.toByteArray())
    override fun getName(): String = firebaseUid
}
