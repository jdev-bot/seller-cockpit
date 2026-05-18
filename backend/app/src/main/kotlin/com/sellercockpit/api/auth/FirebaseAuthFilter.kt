package com.sellercockpit.api.auth

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.PreMatching
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.ext.Provider
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URL
import java.security.Principal
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Validates Firebase ID tokens from Authorization: Bearer ***
 * Does NOT enforce auth on /health, /q/* (metrics/openapi), or /api/auth/verify.
 *
 * Token verification:
 *   1. Split JWT header.payload.signature
 *   2. Verify signature against Google's public certificate (fetched from JWKS URL)
 *   3. Validate exp, iss, aud claims
 *
 * Production: enable full signature validation by setting FIREBASE_JWKS_URL
 *   or using the Firebase Admin SDK for stronger guarantees.
 */
@Provider
@PreMatching
class FirebaseAuthFilter : ContainerRequestFilter {

    @ConfigProperty(name = "firebase.project-id")
    lateinit var firebaseProjectId: String

    @ConfigProperty(name = "mp.jwt.verify.publickey.location", defaultValue = "")
    lateinit var jwksUrl: String

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

    /**
     * Validate JWT signature (if JWKS URL configured) and decode claims.
     * If JWKS URL is not set, falls back to claim-only validation (DEV MODE).
     */
    private fun validateAndDecode(token: String): Map<String, Any>? {
        try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            val headerJson = String(Base64.getUrlDecoder().decode(parts[0]))
            val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]))
            val signature = Base64.getUrlDecoder().decode(parts[2])

            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val header = mapper.readValue(headerJson, Map::class.java) as Map<String, Any>
            val payload = mapper.readValue(payloadJson, Map::class.java) as Map<String, Any>

            // Validate signature if JWKS URL is configured
            if (jwksUrl.isNotBlank()) {
                val kid = header["kid"] as? String ?: return null
                val cert = fetchCertificate(jwksUrl, kid) ?: return null
                val sigValid = verifySignature(parts[0] + "." + parts[1], signature, cert)
                if (!sigValid) return null
            }

            // Validate claims
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

    /**
     * Fetch X.509 certificate from JWKS endpoint.
     */
    private fun fetchCertificate(jwksUrl: String, kid: String): X509Certificate? {
        return try {
            val url = URL(jwksUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.inputStream.use { stream ->
                val json = com.fasterxml.jackson.databind.ObjectMapper().readTree(stream)
                val keys = json.get("keys") ?: json  // Support both JWK set and direct cert
                if (keys.isArray) {
                    for (key in keys) {
                        if (key.get("kid")?.asText() == kid) {
                            val x5c = key.get("x5c")?.get(0)?.asText() ?: continue
                            val certBytes = Base64.getDecoder().decode(x5c)
                            val factory = java.security.cert.CertificateFactory.getInstance("X.509")
                            return factory.generateCertificate(certBytes.inputStream()) as X509Certificate
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verify JWT signature using SHA256withRSA.
     */
    private fun verifySignature(signedContent: String, signature: ByteArray, cert: X509Certificate): Boolean {
        return try {
            val sig = java.security.Signature.getInstance("SHA256withRSA")
            sig.initVerify(cert.publicKey)
            sig.update(signedContent.toByteArray())
            sig.verify(signature)
        } catch (e: Exception) {
            false
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
