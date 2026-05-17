package com.sellercockpit.api.auth

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.PreMatching
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.security.Principal
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Validates Firebase ID tokens from Authorization: Bearer header.
 * Does NOT enforce auth on /health, /q/* (metrics/openapi), or /api/auth/verify.
 *
 * Token verification is lightweight: split JWT, check claims.
 * Full Firebase Admin SDK is heavy; we do manual validation to avoid the dependency.
 * NOTE: In production, validate signature against Google JWKS:
 * https://www.googleapis.com/service_accounts/v1/metadata/x509/securetoken@system.gserviceaccount.com
 */
@Provider
@PreMatching
class FirebaseAuthFilter : ContainerRequestFilter {

    @ConfigProperty(name = "firebase.project-id")
    lateinit var firebaseProjectId: String

    companion object {
        val PUBLIC_PATHS = setOf(
            "/health", "/q/health", "/q/metrics",
            "/api/auth/verify", "/swagger-ui", "/openapi", "/api/health"
        )
    }

    override fun filter(requestContext: ContainerRequestContext) {
        val path = requestContext.uriInfo.path
        if (PUBLIC_PATHS.any { path.startsWith(it) }) return

        val authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            requestContext.abortWith(jakarta.ws.rs.core.Response.status(401).entity("Missing Authorization header").build())
            return
        }

        val token = authHeader.substringAfter("Bearer ").trim()
        val claims = validateAndDecode(token)
        if (claims == null) {
            requestContext.abortWith(jakarta.ws.rs.core.Response.status(401).entity("Invalid or expired token").build())
            return
        }

        val uid = claims["sub"] as? String ?: return requestContext.abortWith(
            jakarta.ws.rs.core.Response.status(401).entity("Missing sub claim").build()
        )
        val email = claims["email"] as? String
        val name = claims["name"] as? String

        val authenticatedUser = AuthenticatedUser(
            firebaseUid = uid,
            email = email,
            displayName = name
        )
        requestContext.securityContext = FirebaseSecurityContext(authenticatedUser)
    }

    private fun validateAndDecode(token: String): Map<String, Any>? {
        try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]))
            val payload = com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(payloadJson, Map::class.java) as Map<String, Any>

            val exp = (payload["exp"] as? Number)?.toLong() ?: return null
            if (Instant.now().epochSecond >= exp) return null

            val iss = payload["iss"] as? String ?: return null
            if (!iss.startsWith("https://securetoken.google.com/$firebaseProjectId")) return null

            val aud = payload["aud"] as? String ?: return null
            if (aud != firebaseProjectId) return null

            return payload
        } catch (e: Exception) {
            return null
        }
    }
}

private class FirebaseSecurityContext(
    private val user: AuthenticatedUser
) : jakarta.ws.rs.core.SecurityContext {
    override fun getUserPrincipal(): Principal = user
    override fun isUserInRole(role: String?): Boolean = true
    override fun isSecure(): Boolean = true
    override fun getAuthenticationScheme(): String = "Bearer"
}
