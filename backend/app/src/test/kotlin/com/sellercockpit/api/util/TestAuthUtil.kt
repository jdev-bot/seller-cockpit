package com.sellercockpit.api.util

import com.sellercockpit.api.auth.AuthenticatedUser
import io.quarkus.test.security.TestSecurity
import io.quarkus.test.security.SecurityAttribute
import java.util.UUID

/**
 * Utility for creating test security contexts.
 * Usage on test class or method: @TestSecurity(user = "test", roles = ["user"])
 *
 * For full auth context injection, use this helper to create an AuthenticatedUser.
 */
object TestAuthUtil {
    const val TEST_FIREBASE_UID = "firebase-test-uid-123"
    const val TEST_EMAIL = "test@example.com"
    const val TEST_USER_ID: String = "550e8400-e29b-41d4-a716-446655440000"

    fun createTestUser(): AuthenticatedUser = AuthenticatedUser(
        firebaseUid = TEST_FIREBASE_UID,
        email = TEST_EMAIL,
        displayName = "Test User"
    )

    fun testUserId(): UUID = UUID.fromString(TEST_USER_ID)
}
