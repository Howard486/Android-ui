package com.foldspace.launcher.microsoft

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.tokenStore: DataStore<Preferences> by preferencesDataStore(name = "microsoft")

/**
 * The refresh token, sealed with a key the app cannot export.
 *
 * A refresh token is a long-lived credential to somebody's calendar and tasks;
 * writing it as plain text into a preferences file would make it readable by
 * anything that can read this app's data directory — which on a rooted or
 * backed-up device is more than nothing.
 *
 * Hand-rolled AES-GCM over the Android Keystore rather than
 * `androidx.security:security-crypto`, which is in maintenance and adds a
 * dependency for sixty lines of work. The key never leaves the keystore; only
 * the ciphertext is stored.
 */
class TokenStore(private val context: Context) {

    suspend fun read(): MicrosoftTokens? {
        val prefs = context.tokenStore.data.first()
        val refresh = prefs[Keys.Refresh]?.let(::decrypt)
        val access = prefs[Keys.Access]?.let(::decrypt)
        val expiry = prefs[Keys.Expiry]?.toLongOrNull() ?: 0L
        if (refresh == null && access == null) return null
        return MicrosoftTokens(
            accessToken = access.orEmpty(),
            refreshToken = refresh,
            expiresAtMillis = expiry,
        )
    }

    suspend fun write(tokens: MicrosoftTokens) {
        context.tokenStore.edit { prefs ->
            encrypt(tokens.accessToken)?.let { prefs[Keys.Access] = it }
            // A refresh response may omit the refresh token, and it means
            // "keep the one you have" — not "you no longer have one".
            tokens.refreshToken?.let { token -> encrypt(token)?.let { prefs[Keys.Refresh] = it } }
            prefs[Keys.Expiry] = tokens.expiresAtMillis.toString()
        }
    }

    suspend fun clear() {
        context.tokenStore.edit { it.clear() }
    }

    private fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        // The IV is generated per encryption and stored alongside: reusing one
        // with GCM is the single mistake that breaks the whole construction.
        encode(cipher.iv) + SEPARATOR + encode(body)
    }.getOrNull()

    private fun decrypt(stored: String): String? = runCatching {
        val (rawIv, rawBody) = stored.split(SEPARATOR).takeIf { it.size == 2 }
            ?: return@runCatching null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, decode(rawIv)))
        String(cipher.doFinal(decode(rawBody)), Charsets.UTF_8)
    }.getOrNull()

    /**
     * The keystore key, created once.
     *
     * Deliberately not `setUserAuthenticationRequired`: the calendar card
     * refreshes while the launcher is on screen, and requiring a fingerprint
     * for each refresh would make the page unusable. The app lock is the
     * feature for that, and it is a separate decision.
     */
    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keystore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String) = Base64.getDecoder().decode(value)

    private object Keys {
        val Access = stringPreferencesKey("access")
        val Refresh = stringPreferencesKey("refresh")
        val Expiry = stringPreferencesKey("expiry")
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "foldspace.microsoft.tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}
