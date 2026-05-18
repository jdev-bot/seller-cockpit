package com.sellercockpit.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.ws.rs.core.HttpHeaders
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.*

@QuarkusTest
class ProductCaseResourceTest {

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
    fun `create and retrieve product case`() {
        // Ensure test user exists
        given()
            .contentType(ContentType.JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${testToken()}")
            .post("/api/auth/verify")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))

        // Create
        val response = given()
            .contentType(ContentType.JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${testToken()}")
            .body("""{"sellerMode": "PRIVATE_DECLUTTERING", "title": "Test Keyboard"}""")
            .post("/api/product-cases")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("sellerMode", equalTo("PRIVATE_DECLUTTERING"))
            .body("status", equalTo("CAPTURED"))
            .body("title", equalTo("Test Keyboard"))
            .extract()
            .jsonPath()

        val id = response.getString("id")

        // Retrieve
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${testToken()}")
            .get("/api/product-cases/$id")
            .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("sellerMode", equalTo("PRIVATE_DECLUTTERING"))

        // List
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${testToken()}")
            .get("/api/product-cases")
            .then()
            .statusCode(200)
            .body("items", notNullValue())

        // Delete
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${testToken()}")
            .delete("/api/product-cases/$id")
            .then()
            .statusCode(204)
    }

    @Test
    fun `dashboard returns list`() {
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${testToken()}")
            .get("/api/dashboard")
            .then()
            .statusCode(200)
            .body("productCases", notNullValue())
    }

    @Test
    fun `health endpoints return ok`() {
        given().get("/health").then().statusCode(200).body(equalTo("UP"))
        given().get("/health/ready").then().statusCode(200).body(equalTo("READY"))
    }

    @Test
    fun `marketplace connections endpoint returns list`() {
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${testToken()}")
            .get("/api/marketplaces/connections")
            .then()
            .statusCode(200)
    }
}
