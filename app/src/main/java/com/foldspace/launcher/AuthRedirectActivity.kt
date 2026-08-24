package com.foldspace.launcher

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Catches the browser coming back from a Microsoft sign-in.
 *
 * A custom scheme rather than MSAL's `msauth://<pkg>/<signature hash>`: this
 * project signs its release build with the debug config and commits no
 * keystore, so the signing certificate — and therefore that hash — is
 * regenerated on every CI runner. A redirect URI bound to it would break on
 * nearly every build.
 *
 * Invisible, like the pin handler. The user has already spent a browser round
 * trip; a confirmation screen on the way back would be one more tap for no
 * information.
 */
class AuthRedirectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val redirect = intent.data?.toString()
        if (redirect == null) {
            finish()
            return
        }

        val container = (application as FoldSpaceApplication).container
        lifecycleScope.launch {
            val clientId = container.settings.settings.value.microsoftClientId
            val failure = container.microsoft.completeSignIn(clientId, redirect)
            // A failure also stays on the Microsoft card, which is where the
            // user will actually read it — a toast is a two-second glimpse of
            // something that needs a settings change to fix.
            Toast.makeText(
                this@AuthRedirectActivity,
                failure ?: "已連結 Microsoft 帳戶",
                if (failure == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }
}
