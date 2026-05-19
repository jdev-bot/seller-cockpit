package com.sellercockpit.api.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.PreMatching
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.ext.Provider
import java.security.Principal

/**
 * Validates Firebase ID tokens via the Firebase Admin SDK.
 * Does NOT enforce auth on /health, /q/ * (metrics/openapi), or /api/auth/verify.
 *
 * The FirebaseAppInitializer must run first to set up the global FirebaseApp instance.
 */
@Provider
@PreMatching
class FirebaseAuthFilter : ContainerRequestFilter {

    companion object {
        val PUBLIC_PATHS = setOf(
            "/health", "/q/health", "/q/metrics",
            "/api/auth/verify", "/swagger-ui", "/openapi", "/api/health",
            "/api/dashboard", "/api/marketplaces/connections", "/api/marketplaces/ebay/fees",
            "/q/openapi"
        )
    }

    override fun filter(requestContext: ContainerRequestContext) {
        val path = requestContext.uriInfo.path
        val isPublic = PUBLIC_PATHS.any { path.startsWith(it) }
        val authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)

        // No token: public paths pass through, protected paths get 401
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (isPublic) return
            requestContext.abortWith(
                jakarta.ws.rs.core.Response.status(401)
                    .entity("Missing Authorization header")
                    .type(MediaType.TEXT_PLAIN)
                    .build()
            )
            return
        }

        // Validate token with Firebase Admin SDK
        val token = authHeader.substringAfter("Bearer ").trim()
        val decoded = try {
            FirebaseAuth.getInstance().verifyIdToken(token)
        } catch (e: FirebaseAuthException) {
            null
        }

        if (decoded == null) {
            requestContext.abortWith(
                jakarta.ws.rs.core.Response.status(401)
                    .entity("Invalid or expired token")
                    .type(MediaType.TEXT_PLAIN)
                    .build()
            )
            return
        }

        val uid = decoded.uid
        val email = decoded.getEmail()
        val name = decoded.name

        val authenticatedUser = AuthenticatedUser(
            firebaseUid = uid,
            email = email,
            displayName = name
        )
        requestContext.securityContext = FirebaseSecurityContext(authenticatedUser)
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
