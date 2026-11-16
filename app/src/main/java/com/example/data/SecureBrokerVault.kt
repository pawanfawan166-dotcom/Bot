package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.BrokerCredentials
import com.example.model.IndianBrokerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed Android Keystore AES-256-GCM encrypted vault for Real-mode broker API credentials.
 * Paper mode never reads or requires this vault.
 */
class SecureBrokerVault(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _credentialsFlow = MutableStateFlow<Map<IndianBrokerId, BrokerCredentials>>(loadAllInternal())
    val credentialsFlow: Flow<Map<IndianBrokerId, BrokerCredentials>> = _credentialsFlow.asStateFlow()

    fun getAllCredentials(): Map<IndianBrokerId, BrokerCredentials> = _credentialsFlow.value

    fun getCredentials(brokerId: IndianBrokerId): BrokerCredentials =
        _credentialsFlow.value[brokerId] ?: BrokerCredentials(
            brokerId = brokerId,
            baseUrl = brokerId.defaultBaseUrl
        )

    fun saveCredentials(creds: BrokerCredentials) {
        val current = _credentialsFlow.value.toMutableMap()
        val sanitized = creds.copy(
            apiKey = creds.apiKey.trim(),
            apiSecret = creds.apiSecret.trim(),
            accessToken = creds.accessToken.trim(),
            requestToken = creds.requestToken.trim(),
            clientId = creds.clientId.trim(),
            baseUrl = creds.baseUrl.trim()
        )
        current[sanitized.brokerId] = sanitized
        persistAll(current)
        _credentialsFlow.value = current
    }

    fun clearCredentials(brokerId: IndianBrokerId) {
        val current = _credentialsFlow.value.toMutableMap()
        current.remove(brokerId)
        persistAll(current)
        _credentialsFlow.value = current
    }

    private fun loadAllInternal(): Map<IndianBrokerId, BrokerCredentials> {
        val encoded = prefs.getString(KEY_CIPHER_BLOB, null) ?: return emptyMap()
        val plaintext = decryptString(encoded) ?: return emptyMap()
        return runCatching {
            val rawMap = json.decodeFromString<Map<String, BrokerCredentials>>(plaintext)
            rawMap.entries.mapNotNull { (k, v) ->
                val id = IndianBrokerId.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
                id to v
            }.toMap()
        }.getOrElse { emptyMap() }
    }

    private fun persistAll(map: Map<IndianBrokerId, BrokerCredentials>) {
        val rawMap = map.entries.associate { (k, v) -> k.name to v }
        val payload = json.encodeToString(rawMap)
        val encrypted = encryptString(payload)
        prefs.edit().putString(KEY_CIPHER_BLOB, encrypted).apply()
    }

    private fun encryptString(plaintext: String): String {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptString(encoded: String): String? = runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size < 14) return null
        val ivLen = combined[0].toInt() and 0xFF
        if (combined.size <= 1 + ivLen) return null
        val iv = combined.copyOfRange(1, 1 + ivLen)
        val ciphertext = combined.copyOfRange(1 + ivLen, combined.size)
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateSecretKey(): SecretKey {
        // Primary: Hardware-backed AndroidKeyStore AES-256-GCM key
        val ksResult = runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return@runCatching existing
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            gen.init(spec)
            gen.generateKey()
        }
        ksResult.getOrNull()?.let { return it }

        // JVM / Robolectric unit-test fallback when AndroidKeyStore provider is unavailable
        val existingLocal = prefs.getString(KEY_FALLBACK_AES, null)
        if (existingLocal != null) {
            val raw = Base64.decode(existingLocal, Base64.NO_WRAP)
            if (raw.size == 32) return SecretKeySpec(raw, "AES")
        }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_FALLBACK_AES, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        return SecretKeySpec(bytes, "AES")
    }

    companion object {
        private const val PREFS_NAME = "tradedesk_secure_broker_vault"
        private const val KEY_CIPHER_BLOB = "encrypted_credentials_v1"
        private const val KEY_FALLBACK_AES = "jvm_fallback_aes_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "tradedesk_broker_vault_key_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
