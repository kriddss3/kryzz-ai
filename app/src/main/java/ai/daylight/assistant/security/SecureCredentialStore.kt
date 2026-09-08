package ai.daylight.assistant.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** API keys are encrypted by a non-exportable Android Keystore AES key before persistence. */
class SecureCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_credentials", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    init {
        // Voice STT is OpenRouter-only. Drop any leftover xAI key from older builds.
        if (preferences.contains("xai")) preferences.edit().remove("xai").apply()
    }

    fun hasOpenRouterKey(): Boolean = preferences.contains(OPENROUTER)
    fun hasParallelKey(): Boolean = preferences.contains(PARALLEL)
    fun hasFishKey(): Boolean = preferences.contains(FISH)
    fun hasMinimaxKey(): Boolean = preferences.contains(MINIMAX)
    fun openRouterKey(): String? = decrypt(preferences.getString(OPENROUTER, null))
    fun parallelKey(): String? = decrypt(preferences.getString(PARALLEL, null))
    fun fishKey(): String? = decrypt(preferences.getString(FISH, null))
    fun minimaxKey(): String? = decrypt(preferences.getString(MINIMAX, null))

    fun setOpenRouterKey(value: String) = put(OPENROUTER, value)
    fun setParallelKey(value: String) = put(PARALLEL, value)
    fun setFishKey(value: String) = put(FISH, value)
    fun setMinimaxKey(value: String) = put(MINIMAX, value)

    fun clear() {
        preferences.edit().clear().apply()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun put(name: String, value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) preferences.edit().remove(name).apply()
        else preferences.edit().putString(name, encrypt(clean)).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_SIZE)))
            cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "daylight_api_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val OPENROUTER = "openrouter"
        const val PARALLEL = "parallel"
        const val FISH = "fish_audio"
        const val MINIMAX = "minimax"
    }
}
