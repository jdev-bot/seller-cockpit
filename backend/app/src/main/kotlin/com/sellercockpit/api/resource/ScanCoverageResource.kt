package com.sellercockpit.api.resource

import com.sellercockpit.api.model.ScanCoverageRequest
import com.sellercockpit.api.model.ScanCoverageResponse
import com.sellercockpit.api.service.ScanCoverageService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.SecurityContext
import java.util.UUID

@Path("/api/product-cases/{productCaseId}/scan-coverage")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ScanCoverageResource @Inject constructor(
    private val scanCoverageService: ScanCoverageService
) {
    @Context
    lateinit var securityContext: SecurityContext

    @POST
    fun saveScanCoverage(
        @PathParam("productCaseId") productCaseId: UUID,
        request: ScanCoverageRequest
    ): ScanCoverageResponse {
        return scanCoverageService.saveScanCoverage(getCurrentUserId(), productCaseId, request)
    }

    @GET
    fun getScanCoverage(@PathParam("productCaseId") productCaseId: UUID): ScanCoverageResponse? {
        return scanCoverageService.getScanCoverage(getCurrentUserId(), productCaseId)
    }

    private fun getCurrentUserId(): UUID {
        val firebaseUid = securityContext.userPrincipal?.name
            ?: throw NotFoundException("Not authenticated")
        val entity = com.sellercockpit.api.model.UserEntity.find("firebaseUid", firebaseUid).firstResult()
            ?: throw NotFoundException("User not registered")
        return entity.id
    }
}
