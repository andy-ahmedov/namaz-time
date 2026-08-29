package ru.namaztime.tv.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.net.URI
import java.security.KeyStore
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val DEVICE_PROVISIONING_PREFERENCES = "device_provisioning_encrypted"
const val DEVICE_PROVISIONING_CIPHERTEXT = "provisioning_ciphertext_v1"
private const val DEVICE_PROVISIONING_KEY_ALIAS = "namaz_time_device_provisioning_v1"
private const val ENVELOPE_VERSION: Byte = 1
private val provisioningAad = "namaz-time/device-provisioning/v1".encodeToByteArray()

fun interface SecretKeyProvider {
    fun getOrCreate(): SecretKey
}

class AndroidKeystoreSecretKeyProvider(
    private val alias: String = DEVICE_PROVISIONING_KEY_ALIAS,
) : SecretKeyProvider {
    override fun getOrCreate(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}

class EncryptedDeviceProvisioningStore(
    context: Context,
    private val secretKeyProvider: SecretKeyProvider = AndroidKeystoreSecretKeyProvider(),
) : DeviceProvisioningStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        DEVICE_PROVISIONING_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun load(): DeviceProvisioning? {
        val encoded = preferences.getString(DEVICE_PROVISIONING_CIPHERTEXT, null) ?: return null
        return try {
            val envelope = Base64.decode(encoded, Base64.NO_WRAP)
            if (envelope.size < 2 || envelope[0] != ENVELOPE_VERSION) {
                throw IOException("unsupported encrypted provisioning envelope")
            }
            val ivLength = envelope[1].toInt() and 0xff
            if (ivLength !in 12..16 || envelope.size <= 2 + ivLength) {
                throw IOException("invalid encrypted provisioning envelope")
            }
            val iv = envelope.copyOfRange(2, 2 + ivLength)
            val ciphertext = envelope.copyOfRange(2 + ivLength, envelope.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKeyProvider.getOrCreate(), GCMParameterSpec(128, iv))
            cipher.updateAAD(provisioningAad)
            val plaintext = cipher.doFinal(ciphertext)
            val provisioning = provisioningJson.decodeFromString<DeviceProvisioning>(
                plaintext.decodeToString(throwOnInvalidSequence = true),
            )
            if (!provisioning.isValid()) throw IOException("decrypted provisioning is invalid")
            provisioning
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("device provisioning cannot be decrypted", error)
        }
    }

    override fun save(provisioning: DeviceProvisioning) {
        if (!provisioning.isValid()) throw IOException("device provisioning is invalid")
        try {
            val plaintext = provisioningJson.encodeToString(provisioning).encodeToByteArray()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKeyProvider.getOrCreate())
            cipher.updateAAD(provisioningAad)
            val ciphertext = cipher.doFinal(plaintext)
            val iv = cipher.iv
            val envelope = byteArrayOf(ENVELOPE_VERSION, iv.size.toByte()) + iv + ciphertext
            val encoded = Base64.encodeToString(envelope, Base64.NO_WRAP)
            if (!preferences.edit().putString(DEVICE_PROVISIONING_CIPHERTEXT, encoded).commit()) {
                throw IOException("device provisioning commit failed")
            }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("device provisioning cannot be encrypted", error)
        }
    }

    override fun clear() {
        if (!preferences.edit().remove(DEVICE_PROVISIONING_CIPHERTEXT).commit()) {
            throw IOException("device provisioning clear failed")
        }
    }
}

private val provisioningJson = Json {
    ignoreUnknownKeys = false
    isLenient = false
    coerceInputValues = false
    explicitNulls = false
}

private fun DeviceProvisioning.isValid(): Boolean =
    credentials.deviceId.length in 8..128 && credentials.token.length in 16..4096 &&
        validHttpsOrigin(credentials.manifestUrl) && mosqueId.length in 1..128 &&
        mosqueName.length in 1..240 && isNamedZone(mosqueTimezone) &&
        credentials.mosqueId == mosqueId && credentials.mosqueTimezone == mosqueTimezone

private fun validHttpsOrigin(value: String): Boolean = try {
    val uri = URI(value)
    uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null
} catch (_: Exception) {
    false
}

private fun isNamedZone(value: String): Boolean = try {
    ZoneId.getAvailableZoneIds().contains(value) && ZoneId.of(value).id == value
} catch (_: Exception) {
    false
}
