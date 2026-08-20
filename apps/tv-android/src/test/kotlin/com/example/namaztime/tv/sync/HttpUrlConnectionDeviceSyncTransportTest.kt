package com.example.namaztime.tv.sync

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class HttpUrlConnectionDeviceSyncTransportTest {
    @Test
    fun executesBoundedAuthenticatedHttpsGetWithoutFollowingRedirects() = runTest {
        val connection = FakeHttpUrlConnection(
            responseCodeValue = 200,
            responseHeaders = mapOf("ETag" to listOf("\"manifest-v1\"")),
            responseBody = "manifest".encodeToByteArray(),
        )
        val transport = HttpUrlConnectionDeviceSyncTransport(
            connectionFactory = UrlConnectionFactory { connection },
        )

        val response = transport.execute(
            SyncHttpRequest(
                url = "https://api.example.invalid/v1/manifest",
                bearerToken = "fixture-device-token-not-production",
                ifNoneMatch = "\"manifest-v0\"",
                maximumBodyBytes = 64,
            ),
        )

        assertEquals(200, response.statusCode)
        assertEquals("manifest", response.body.decodeToString())
        assertEquals("\"manifest-v1\"", response.header("etag"))
        assertEquals("GET", connection.requestMethod)
        assertEquals(
            "Bearer fixture-device-token-not-production",
            connection.capturedRequestProperties["Authorization"],
        )
        assertEquals("\"manifest-v0\"", connection.capturedRequestProperties["If-None-Match"])
        assertFalse(connection.instanceFollowRedirects)
        assertEquals(15_000, connection.connectTimeout)
        assertEquals(30_000, connection.readTimeout)
        assertEquals(true, connection.disconnected)
    }

    @Test
    fun rejectsInsecureUrlAndBodiesBeyondDeclaredLimit() = runTest {
        var opens = 0
        val transport = HttpUrlConnectionDeviceSyncTransport(
            connectionFactory = UrlConnectionFactory {
                opens += 1
                FakeHttpUrlConnection(200, emptyMap(), byteArrayOf())
            },
        )
        try {
            transport.execute(
                SyncHttpRequest(
                    url = "http://api.example.invalid/v1/manifest",
                    bearerToken = "fixture-device-token-not-production",
                    maximumBodyBytes = 64,
                ),
            )
            fail("expected insecure URL rejection")
        } catch (_: IOException) {
            // Expected before opening a connection.
        }
        assertEquals(0, opens)

        val oversized = HttpUrlConnectionDeviceSyncTransport(
            connectionFactory = UrlConnectionFactory {
                FakeHttpUrlConnection(
                    responseCodeValue = 200,
                    responseHeaders = mapOf("Content-Length" to listOf("65")),
                    responseBody = ByteArray(65),
                )
            },
        )
        try {
            oversized.execute(
                SyncHttpRequest(
                    url = "https://api.example.invalid/v1/manifest",
                    bearerToken = "fixture-device-token-not-production",
                    maximumBodyBytes = 64,
                ),
            )
            fail("expected oversized body rejection")
        } catch (_: IOException) {
            // Expected before the body can reach the sync coordinator.
        }
    }
}

private class FakeHttpUrlConnection(
    private val responseCodeValue: Int,
    private val responseHeaders: Map<String, List<String>>,
    private val responseBody: ByteArray,
) : HttpURLConnection(URL("https://api.example.invalid")) {
    val capturedRequestProperties = mutableMapOf<String, String>()
    var disconnected = false

    override fun connect() = Unit

    override fun disconnect() {
        disconnected = true
    }

    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int = responseCodeValue

    override fun getHeaderFields(): Map<String, List<String>> = responseHeaders

    override fun getInputStream(): InputStream = ByteArrayInputStream(responseBody)

    override fun getErrorStream(): InputStream = ByteArrayInputStream(responseBody)

    override fun setRequestProperty(key: String, value: String) {
        capturedRequestProperties[key] = value
    }
}
