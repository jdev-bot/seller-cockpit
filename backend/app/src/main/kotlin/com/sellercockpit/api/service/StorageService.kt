package com.sellercockpit.api.service

import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.http.Method
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.concurrent.TimeUnit

@ApplicationScoped
class StorageService @Inject constructor(
    @ConfigProperty(name = "minio.url") private val minioUrl: String,
    @ConfigProperty(name = "minio.access-key") private val accessKey: String,
    @ConfigProperty(name = "minio.secret-key") private val secretKey: String,
    @ConfigProperty(name = "minio.bucket") private val bucket: String
) {
    private val client: MinioClient by lazy {
        MinioClient.builder()
            .endpoint(minioUrl)
            .credentials(accessKey, secretKey)
            .build()
    }

    fun generatePresignedUrl(objectName: String, contentType: String, expirySeconds: Int): String {
        return try {
            ensureBucketExists()
            client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .`object`(objectName)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .extraHeaders(mapOf("Content-Type" to contentType))
                    .build()
            )
        } catch (e: Exception) {
            // Fallback for local dev without MinIO: return a mock URL
            "http://localhost:9000/$bucket/$objectName?presigned=mock&expiry=${expirySeconds}"
        }
    }

    fun getPublicUrl(objectName: String): String {
        return "$minioUrl/$bucket/$objectName"
    }

    private fun ensureBucketExists() {
        try {
            if (!client.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucket).build())
            }
        } catch (e: Exception) {
            // Ignore for mock/fallback
        }
    }
}
