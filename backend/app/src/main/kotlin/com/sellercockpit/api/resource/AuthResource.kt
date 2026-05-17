package com.sellercockpit.api.resource

import com.sellercockpit.api.auth.AuthenticatedUser
import com.sellercockpit.api.model.UserEntity
import com.sellercockpit.api.service.UserService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.SecurityContext
import java.util.UUID

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuthResource @Inject constructor(
    private val userService: UserService
) {

    @POST
    @Path("/verify")
    fun verifyToken(@Context sc: SecurityContext): AuthVerifyResponse {
        val user = sc.userPrincipal as? AuthenticatedUser?.let {
            val entity = userService.findOrCreateUser(it)
            AuthVerifyResponse(
                userId = entity.id.toString(),
                firebaseUid = it.firebaseUid,
                email = it.email,
                displayName = it.displayName,
                registered = true
            )
        } ?: AuthVerifyResponse(
            userId = "",
            firebaseUid = "",
            email = null,
            displayName = null,
            registered = false
        )
        return user
    }

    @GET
    @Path("/profile")
    fun getProfile(@Context sc: SecurityContext): UserProfileResponse {
        val user = sc.userPrincipal as? AuthenticatedUser
            ?: throw ForbiddenException("Not authenticated")
        val entity = UserEntity.find("firebaseUid", user.firebaseUid).firstResult()
            ?: throw NotFoundException("User not found")
        return UserProfileResponse(
            id = entity.id.toString(),
            firebaseUid = user.firebaseUid,
            email = entity.email,
            displayName = entity.displayName,
            photoUrl = entity.photoUrl,
            createdAt = entity.createdAt.toString()
        )
    }
}

data class AuthVerifyResponse(
    val userId: String,
    val firebaseUid: String,
    val email: String?,
    val displayName: String?,
    val registered: Boolean
)

data class UserProfileResponse(
    val id: String,
    val firebaseUid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val createdAt: String
)

class ForbiddenException(message: String) : jakarta.ws.rs.WebApplicationException(
    jakarta.ws.rs.core.Response.status(403).entity(message).build()
)
