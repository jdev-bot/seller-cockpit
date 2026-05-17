package com.sellercockpit.api.resource

import com.sellercockpit.api.service.ProductCaseService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class NotificationResource @Inject constructor(
    private val productCaseService: ProductCaseService
) {

    @POST
    @Path("/register-push-token")
    fun registerPushToken(request: PushTokenRequest): Response {
        // Store push token per user for Expo notifications
        // In production: persist to database, schedule via Expo Push Service
        return Response.ok(mapOf("registered" to true, "token" to request.token.take(8) + "...")).build()
    }

    @POST
    @Path("/send-test")
    fun sendTestNotification(request: PushNotificationRequest): Response {
        // Send via Expo Push Service or FCM/APNs
        return Response.ok(mapOf("sent" to true, "message" to request.message)).build()
    }

    private fun getCurrentUserId(): UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
}

data class PushTokenRequest(
    val platform: String, // "ios" or "android"
    val token: String
)

data class PushNotificationRequest(
    val title: String,
    val message: String,
    val productCaseId: String? = null
)
