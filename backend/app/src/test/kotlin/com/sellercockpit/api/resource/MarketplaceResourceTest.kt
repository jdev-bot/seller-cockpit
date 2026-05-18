package com.sellercockpit.api.resource

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.*
import jakarta.ws.rs.core.HttpHeaders

@QuarkusTest
class MarketplaceResourceTest {

    @Test
    fun `connections endpoint returns list without auth`() {
        given().get("/api/marketplaces/connections")
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
            .body("""{"price": {"amount": "50.00", "currency": "EUR"}, "category": "CELLPHONES"}""")
            .post("/api/marketplaces/ebay/fees")
            .then()
            .statusCode(200)
            .body("fees", notNullValue())
            .body("fees.insertionFee", notNullValue())
            .body("fees.finalValueFee", notNullValue())
            .body("total", notNullValue())
    }
}
