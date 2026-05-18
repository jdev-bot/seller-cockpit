package com.sellercockpit.api.service

import com.sellercockpit.api.model.*
import com.sellercockpit.api.repository.ProductCaseRepository
import com.sellercockpit.api.repository.ScanCoverageRepository
import com.sellercockpit.domain.model.ProductCaseStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.NotFoundException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class ScanCoverageService @Inject constructor(
    private val scanCoverageRepository: ScanCoverageRepository,
    private val productCaseRepository: ProductCaseRepository
) {

    @Transactional
    fun saveScanCoverage(
        userId: UUID,
        productCaseId: UUID,
        request: ScanCoverageRequest
    ): ScanCoverageResponse {
        val entity = productCaseRepository.findById(productCaseId)
            ?: throw NotFoundException("Product case not found")
        if (entity.userId != userId) throw NotFoundException()

        val existing = scanCoverageRepository.findByProductCaseId(productCaseId)

        val coverage = if (existing != null) {
            existing.gridRows = request.gridRows
            existing.gridCols = request.gridCols
            existing.coveredCells = request.coveredCells
            existing.totalCells = request.totalCells
            existing.coveragePercent = request.coveragePercent
            existing.elapsedMs = request.elapsedMs
            existing.isComplete = request.isComplete
            existing.autoStopped = request.autoStopped
            existing.cellData = Json.encodeToString(request.cellData)
            existing.missingRegions = Json.encodeToString(request.missingRegions)
            existing.updatedAt = Instant.now()
            existing
        } else {
            val newEntity = ScanCoverageEntity().apply {
                id = UUID.randomUUID()
                this.productCaseId = productCaseId
                gridRows = request.gridRows
                gridCols = request.gridCols
                coveredCells = request.coveredCells
                totalCells = request.totalCells
                coveragePercent = request.coveragePercent
                elapsedMs = request.elapsedMs
                isComplete = request.isComplete
                autoStopped = request.autoStopped
                cellData = Json.encodeToString(request.cellData)
                missingRegions = Json.encodeToString(request.missingRegions)
                createdAt = Instant.now()
                updatedAt = Instant.now()
            }
            scanCoverageRepository.persist(newEntity)
            newEntity
        }

        // Update product case status if scan is complete
        if (request.isComplete && entity.status == ProductCaseStatus.CAPTURED) {
            entity.status = ProductCaseStatus.PROCESSING_MEDIA
        }

        return toResponse(coverage)
    }

    fun getScanCoverage(userId: UUID, productCaseId: UUID): ScanCoverageResponse? {
        val entity = productCaseRepository.findById(productCaseId)
            ?: throw NotFoundException()
        if (entity.userId != userId) throw NotFoundException()

        val coverage = scanCoverageRepository.findByProductCaseId(productCaseId)
            ?: return null

        return toResponse(coverage)
    }

    private fun toResponse(entity: ScanCoverageEntity): ScanCoverageResponse {
        val cellData = try {
            Json.decodeFromString<List<Boolean>>(entity.cellData)
        } catch (e: Exception) {
            emptyList<Boolean>()
        }
        val missingRegions = try {
            Json.decodeFromString<List<MissingRegionDto>>(entity.missingRegions)
        } catch (e: Exception) {
            emptyList<MissingRegionDto>()
        }

        return ScanCoverageResponse(
            id = entity.id.toString(),
            productCaseId = entity.productCaseId.toString(),
            gridRows = entity.gridRows,
            gridCols = entity.gridCols,
            coveredCells = entity.coveredCells,
            totalCells = entity.totalCells,
            coveragePercent = entity.coveragePercent,
            elapsedMs = entity.elapsedMs,
            isComplete = entity.isComplete,
            autoStopped = entity.autoStopped,
            cellData = cellData,
            missingRegions = missingRegions,
            createdAt = entity.createdAt.toString(),
            updatedAt = entity.updatedAt.toString()
        )
    }
}
