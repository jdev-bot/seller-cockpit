package com.sellercockpit.api.auth

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.*
import jakarta.ws.rs.core.HttpHeaders

@QuarkusTest
class FirebaseAuthFilterTest {

    companion object {
        fun testToken(): String {
            val header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"alg":"RS256","kid":"test"}""".toByteArray())
            val payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"iss":"https://securetoken.google.com/test-project-id","aud":"test-project-id","exp":9999999999,"sub":"test-uid-123","email":"test@test.com","name":"Test User"}""".toByteArray())
            val signature = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest("dummy".toByteArray()))
            return "$header.$payload.$signature"
        }
    }

    @Test
    fun `public paths are accessible without auth`() {
        given().get("/health").then().statusCode(anyOf(equalTo(200), equalTo(404)))
        given().get("/q/health").then().statusCode(200)
        given().get("/q/openapi").then().statusCode(200)
    }

    @Test
    fun `protected paths reject missing auth`() {
        given().get("/api/product-cases")
            .then()
            .statusCode(401)
            .body(containsString("Missing"))
    }

    @Test
    fun `protected paths reject invalid token`() {
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            .get("/api/product-cases")
            .then()
            .statusCode(401)
            .body(containsString("Invalid"))
    }

    @Test
    fun `protected paths reject expired token`() {
        // A JWT with exp in the past
        val expiredToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6InRlc3QifQ." +
            "eyJpc3MiOiJodHRwczovL3NlY3VyZXRva2VuLmdvb2dsZS5jb20vdGVzdCIs" +
            "ImF1ZCI6InRlc3QiLCJleHAiOjE1MDAwMDAwMDAsInN1YiI6InRlc3QtdWlkIn0.signature"
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer $expiredToken")
            .get("/api/product-cases")
            .then()
            .statusCode(401)
    }

    @Test
    @TestSecurity(user = "firebase-test-uid-123", roles = ["user"])
    fun `auth verify returns user info when authenticated`() {
        given()
            .contentType(ContentType.JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${testToken()}")
            .post("/api/auth/verify")
            .then()
            .statusCode(200)
            .body("registered", notNullValue())
            .body("firebaseUid", notNullValue())
    }
}
