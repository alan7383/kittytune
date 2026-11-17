package com.alananasss.kittytune.ui.profile.integrations

import android.content.Context
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.util.UUID

/**
 * "Sign in with Google" the way the official Qobuz app does it.
 *
 * The official app asks Google for an *identity token* (not an access token) and
 * hands that token to Qobuz, which exchanges it for a Qobuz session. The same
 * Google Identity stack is used here, so the token has the same shape and the
 * same audience.
 *
 * The client id is the one thing Google binds to the calling app: the id has to
 * list this app's package name and signing certificate, otherwise the broker
 * refuses the request. That is why the id can be set in the settings instead of
 * being hard-coded.
 */
object QobuzGoogleSignIn {

    /** The server client id the official Qobuz app ships with. */
    const val DEFAULT_SERVER_CLIENT_ID = "1054464321085-nheojooso91dk1v8qkpjkcca61doe7s9.apps.googleusercontent.com"

    fun serverClientId(configured: String): String =
        configured.trim().ifBlank { DEFAULT_SERVER_CLIENT_ID }

    /**
     * Google Play Services refuses native ID token issuance for client IDs that belong
     * to a different package/certificate (error 16 "Account reauth failed" or DEVELOPER_ERROR).
     */
    fun isReauthFailure(message: String?): Boolean {
        if (message == null) return false
        return message.contains("16") ||
            message.contains("reauth failed", ignoreCase = true) ||
            message.contains("DEVELOPER_ERROR", ignoreCase = true)
    }

    /** Runs the Google sign-in and hands the identity token over. */
    fun signIn(
        context: Context,
        clientId: String,
        onToken: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val option = GetSignInWithGoogleOption.Builder(serverClientId(clientId))
            .setNonce(UUID.randomUUID().toString())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        CredentialManager.create(context).getCredentialAsync(
            context,
            request,
            CancellationSignal(),
            ContextCompat.getMainExecutor(context),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val credential = result.credential
                    if (credential !is CustomCredential ||
                        credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        onError("Google did not return an id token")
                        return
                    }
                    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    if (idToken.isNullOrBlank()) {
                        onError("Google did not return an id token")
                    } else {
                        onToken(idToken)
                    }
                }

                override fun onError(e: GetCredentialException) {
                    onError(e.message ?: "Google sign-in failed")
                }
            },
        )
    }
}
