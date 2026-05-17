package com.sellercockpit.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.*

@QuarkusTest
class ProductCaseResourceTest {

    @Test
    fun `create and retrieve product case`() {
        // Create
        val response = given()
            .contentType(ContentType.JSON)
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
            .get("/api/product-cases/$id")
            .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("sellerMode", equalTo("PRIVATE_DECLUTTERING"))

        // List
        given()
            .get("/api/product-cases")
            .then()
            .statusCode(200)
            .body("items", notNullValue())

        // Delete
        given()
            .delete("/api/product-cases/$id")
            .then()
            .statusCode(204)
    }

    @Test
    fun `dashboard returns list`() {
        given()
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
            .get("/api/marketplaces/connections")
            .then()
            .statusCode(200)
    }
}
