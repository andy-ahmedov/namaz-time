package ru.namaztime.tv

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidManifestMediaPermissionSourceTest {
    @Test
    fun mediaStoreFallbackUsesImageOnlyPermissionAndCapsLegacyReadAtApi32() {
        val manifest = locateManifest()
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(manifest)
        val permissions = document.getElementsByTagName("uses-permission")
        val declared = (0 until permissions.length).associate { index ->
            val node = permissions.item(index)
            requireNotNull(
                node.attributes.getNamedItemNS(ANDROID_NAMESPACE, "name").nodeValue,
            ) to
                node.attributes.getNamedItemNS(ANDROID_NAMESPACE, "maxSdkVersion")?.nodeValue
        }

        assertEquals("32", declared["android.permission.READ_EXTERNAL_STORAGE"])
        assertTrue("android.permission.READ_MEDIA_IMAGES" in declared)
        assertEquals(null, declared["android.permission.READ_MEDIA_IMAGES"])
        assertFalse("android.permission.WRITE_EXTERNAL_STORAGE" in declared)
        assertFalse("android.permission.MANAGE_EXTERNAL_STORAGE" in declared)
    }

    private fun locateManifest(): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (true) {
            val fromRoot = File(directory, "apps/tv-android/src/main/AndroidManifest.xml")
            if (fromRoot.isFile) return fromRoot
            val fromModule = File(directory, "src/main/AndroidManifest.xml")
            if (fromModule.isFile) return fromModule
            directory = directory.parentFile ?: error("AndroidManifest.xml not found")
        }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
