package com.sellercockpit.api.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.quarkus.runtime.Startup
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.FileInputStream
import java.io.InputStream

import java.util.Optional

/**
 * Initializes the global Firebase Admin SDK instance on application startup.
 *
 * Supports two configuration modes:
 *   1. Service account JSON file: set FIREBASE_SERVICE_ACCOUNT_PATH to a local file path.
 *   2. Auto-discovery (GCP / Cloud Run): if no file is configured, the SDK picks up
 *      Application Default Credentials from the environment (metadata server).
 *
 * If neither is available, the app still starts but Firebase Auth verification will fail
 * at runtime (unless running in dev mode with mock tokens).
 */
@Startup
class FirebaseAppInitializer {

    @ConfigProperty(name = "firebase.project-id", defaultValue = "test-project-id")
    lateinit var firebaseProjectId: String

    @ConfigProperty(name = "firebase.service-account-path")
    lateinit var serviceAccountPath: Optional<String>

    init {
        if (FirebaseApp.getApps().isEmpty()) {
            val credentials: GoogleCredentials = if (serviceAccountPath.isPresent && serviceAccountPath.get().isNotBlank()) {
                FileInputStream(serviceAccountPath.get()).use { GoogleCredentials.fromStream(it) }
            } else {
                GoogleCredentials.getApplicationDefault()
            }

            val options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(firebaseProjectId)
                .build()

            FirebaseApp.initializeApp(options)
        }
    }
}
