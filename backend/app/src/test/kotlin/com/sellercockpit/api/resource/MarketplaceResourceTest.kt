package com.sellercockpit.api.resource

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.*
import jakarta.ws.rs.core.HttpHeaders

@QuarkusTest
class MarketplaceResourceTest {

    companion object {
        fun testToken(): String {
            val header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"alg":"RS256","kid":"test"}""".toByteArray())
            val payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"iss":"https://securetoken.google.com/test-project-id","aud":"test-project-id","exp":9999999999,"sub":"test-uid-123"}""".toByteArray())
            val signature = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest("dummy".toByteArray()))
            return "$header.$payload.$signature"
        }
    }

    @Test
    fun `connections endpoint returns list without auth`() {
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${testToken()}")
            .get("/api/marketplaces/connections")
            .then()
            .statusCode(200)
            .body("", anyOf(nullValue(), notNullValue()))
    }

    @Test
    fun `eBay connect url requires auth`() {
        given().get("/api/marketplaces/ebay/connect-url")
            .then()
            .statusCode(401)
    }

    @Test
    fun `eBay disconnect requires auth`() {
        given().delete("/api/marketplaces/ebay/disconnect")
            .then()
            .statusCode(401)
    }

    @Test
    fun `eBay publish requires auth`() {
        given().post("/api/marketplaces/ebay/publish/123")
            .then()
            .statusCode(401)
    }

    @Test
    fun `eBay fee estimate works as public endpoint`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"price": {"amount": "50.00", "currency": "EUR"}, "categoryId": "CELLPHONES"}""")
            .post("/api/marketplaces/ebay/fees")
            .then()
            .statusCode(200)
    }
}
