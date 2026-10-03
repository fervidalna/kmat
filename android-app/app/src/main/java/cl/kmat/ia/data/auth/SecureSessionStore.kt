package cl.kmat.ia.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import cl.kmat.ia.domain.model.AuthSession
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Guarda exclusivamente tokens cifrados con una clave no exportable de Android Keystore. */
class SecureSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(session: AuthSession) {
        preferences.edit()
            .putString(ACCESS_TOKEN, encrypt(session.accessToken))
            .putString(REFRESH_TOKEN, encrypt(session.refreshToken))
            .putString(EXPIRES_AT, encrypt(session.expiresAtEpochSeconds.toString()))
            .putString(USER_ID, encrypt(session.userId))
            .apply()
    }

    fun load(): AuthSession? = runCatching {
        val accessToken = decrypt(preferences.getString(ACCESS_TOKEN, null) ?: return null)
        val refreshToken = decrypt(preferences.getString(REFRESH_TOKEN, null) ?: return null)
        val expiresAt = decrypt(preferences.getString(EXPIRES_AT, null) ?: return null).toLong()
        val userId = decrypt(preferences.getString(USER_ID, null) ?: return null)
        AuthSession(accessToken, refreshToken, expiresAt, userId)
    }.getOrElse {
        clear()
        null
    }

    fun hasSession(): Boolean = load() != null

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + SEPARATOR +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(SEPARATOR, limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val PREFERENCES = "kmat_secure_session"
        const val KEY_ALIAS = "kmat.auth.aes.gcm.v1"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val SEPARATOR = ":"
        const val ACCESS_TOKEN = "access_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val EXPIRES_AT = "expires_at"
        const val USER_ID = "user_id"
    }
}
