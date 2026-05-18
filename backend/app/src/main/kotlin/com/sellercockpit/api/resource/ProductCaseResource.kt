package com.sellercockpit.api.resource

import com.sellercockpit.domain.model.PricingRecommendation
import com.sellercockpit.api.model.*
import com.sellercockpit.api.service.ProductCaseService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import java.util.UUID

@Path("/api/product-cases")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ProductCaseResource @Inject constructor(
    private val productCaseService: ProductCaseService
) {
    @Context
    lateinit var securityContext: SecurityContext

    @POST
    fun create(request: CreateProductCaseRequest): Response {
        val userId = getCurrentUserId()
        val result = productCaseService.createProductCase(userId, request)
        return Response.status(Response.Status.CREATED).entity(result).build()
    }

    @GET
    fun list(): ProductCaseListResponse {
        return productCaseService.listProductCases(getCurrentUserId())
    }

    @GET
    @Path("/{id}")
    fun get(@PathParam("id") id: UUID): ProductCaseResponse {
        return productCaseService.getProductCase(getCurrentUserId(), id)
    }

    @PATCH
    @Path("/{id}")
    fun update(@PathParam("id") id: UUID, request: UpdateProductCaseRequest): ProductCaseResponse {
        return productCaseService.updateProductCase(getCurrentUserId(), id, request)
    }

    @DELETE
    @Path("/{id}")
    fun delete(@PathParam("id") id: UUID): Response {
        productCaseService.deleteProductCase(getCurrentUserId(), id)
        return Response.noContent().build()
    }

    @POST
    @Path("/{id}/process-media")
    fun processMedia(@PathParam("id") id: UUID): ProcessMediaResponse {
        return productCaseService.processMedia(getCurrentUserId(), id)
    }

    @GET
    @Path("/{id}/missing-questions")
    fun getMissingQuestions(@PathParam("id") id: UUID): Map<String, List<String>> {
        val pc = productCaseService.getProductCase(getCurrentUserId(), id)
        return mapOf("questions" to pc.missingQuestions)
    }

    @POST
    @Path("/{id}/answers")
    fun answerQuestions(@PathParam("id") id: UUID, request: AnswerQuestionsRequest): ProductCaseResponse {
        return productCaseService.answerQuestions(getCurrentUserId(), id, request)
    }

    @POST
    @Path("/{id}/research")
    fun runResearch(@PathParam("id") id: UUID): ResearchResponse {
        return productCaseService.runResearch(getCurrentUserId(), id)
    }

    @POST
    @Path("/{id}/pricing/recalculate")
    fun recalculatePricing(@PathParam("id") id: UUID, request: PricingRecalculateRequest? = null): PricingRecommendation {
        return productCaseService.recalculatePricing(getCurrentUserId(), id, request)
    }

    @POST
    @Path("/{id}/listing-drafts/generate")
    fun generateListings(@PathParam("id") id: UUID): List<ListingDraftResponse> {
        return productCaseService.generateListings(getCurrentUserId(), id)
    }

    @GET
    @Path("/{id}/listing-drafts")
    fun getListingDrafts(@PathParam("id") id: UUID): List<ListingDraftResponse> {
        return productCaseService.getListingDrafts(getCurrentUserId(), id)
    }

    @POST
    @Path("/listing-drafts/{draftId}/publish")
    fun publishListing(@PathParam("draftId") draftId: UUID, request: PublishRequest): MarketplaceListingResponse {
        return productCaseService.publishListing(getCurrentUserId(), draftId, request)
    }

    @POST
    @Path("/{id}/media/upload-url")
    fun getUploadUrl(@PathParam("id") id: UUID, request: UploadUrlRequest): UploadUrlResponse {
        return productCaseService.createPresignedUploadUrl(getCurrentUserId(), id, request)
    }

    // Authenticated via @Context SecurityContext set by FirebaseAuthFilter
    private fun getCurrentUserId(): UUID {
        val firebaseUid = securityContext.userPrincipal?.name
            ?: throw NotFoundException("Not authenticated")
        val entity = com.sellercockpit.api.model.UserEntity.find("firebaseUid", firebaseUid).firstResult()
            ?: throw NotFoundException("User not registered")
        return entity.id
    }
}
