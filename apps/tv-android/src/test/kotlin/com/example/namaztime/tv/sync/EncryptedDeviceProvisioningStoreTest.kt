package com.example.namaztime.tv.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import javax.crypto.spec.SecretKeySpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class EncryptedDeviceProvisioningStoreTest {
    private lateinit var context: Context
    private val key = SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(DEVICE_PROVISIONING_PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(DEVICE_PROVISIONING_PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun roundTripKeepsTokenAndDeviceIdentityOutOfPlaintextPreferences() {
        val store = EncryptedDeviceProvisioningStore(context, SecretKeyProvider { key })
        val provisioning = fixtureProvisioning()

        store.save(provisioning)

        val persisted = context.getSharedPreferences(
            DEVICE_PROVISIONING_PREFERENCES,
            Context.MODE_PRIVATE,
        ).getString(DEVICE_PROVISIONING_CIPHERTEXT, null) ?: error("ciphertext missing")
        assertFalse(persisted.contains(provisioning.credentials.token))
        assertFalse(persisted.contains(provisioning.credentials.deviceId))
        assertEquals(provisioning, store.load())

        store.clear()
        assertEquals(null, store.load())
    }

    @Test
    fun tamperOrWrongKeyFailsClosedInsteadOfReturningPartialCredentials() {
        val store = EncryptedDeviceProvisioningStore(context, SecretKeyProvider { key })
        store.save(fixtureProvisioning())
        val preferences = context.getSharedPreferences(
            DEVICE_PROVISIONING_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val ciphertext = preferences.getString(DEVICE_PROVISIONING_CIPHERTEXT, null)!!
        preferences.edit().putString(
            DEVICE_PROVISIONING_CIPHERTEXT,
            ciphertext.dropLast(2) + "AA",
        ).commit()
        try {
            store.load()
            fail("expected tamper rejection")
        } catch (_: IOException) {
            // Authentication failure is deliberately opaque.
        }

        store.save(fixtureProvisioning())
        val wrongKey = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        try {
            EncryptedDeviceProvisioningStore(context, SecretKeyProvider { wrongKey }).load()
            fail("expected wrong-key rejection")
        } catch (_: IOException) {
            // Authentication failure is deliberately opaque.
        }
    }

    private fun fixtureProvisioning() = DeviceProvisioning(
        credentials = DeviceSyncCredentials(
            deviceId = "device-fixture-0001",
            token = "fixture-device-token-not-production",
            manifestUrl = "https://api.example.invalid/v1/devices/device-fixture-0001/manifest",
            mosqueId = "mosque-ulsk-second-cathedral",
            mosqueTimezone = "Europe/Ulyanovsk",
        ),
        mosqueId = "mosque-ulsk-second-cathedral",
        mosqueName = "Second Cathedral Mosque of Ulyanovsk",
        mosqueTimezone = "Europe/Ulyanovsk",
    )
}
