package ru.namaztime.tv.sync

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface UrlConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

class HttpUrlConnectionDeviceSyncTransport(
    private val connectionFactory: UrlConnectionFactory = UrlConnectionFactory { url ->
        url.openConnection() as HttpURLConnection
    },
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
) : DeviceSyncTransport {
    override suspend fun execute(request: SyncHttpRequest): SyncHttpResponse =
        withContext(Dispatchers.IO) {
            validateRequest(request)
            val connection = connectionFactory.open(URL(request.url))
            try {
                connection.requestMethod = request.method
                connection.instanceFollowRedirects = false
                connection.connectTimeout = connectTimeoutMillis
                connection.readTimeout = readTimeoutMillis
                connection.useCaches = false
                connection.doInput = true
                connection.doOutput = request.body != null
                connection.setRequestProperty("Accept", "application/json")
                if (request.bearerToken.isNotEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer ${request.bearerToken}")
                }
                request.ifNoneMatch?.let { connection.setRequestProperty("If-None-Match", it) }
                request.contentType?.let { connection.setRequestProperty("Content-Type", it) }
                request.body?.let { body ->
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { output -> output.write(body) }
                }

                val status = connection.responseCode
                val headers = connection.headerFields.entries.mapNotNull { (name, values) ->
                    if (name == null || values.isNullOrEmpty()) null else name to values.first()
                }.toMap()
                headers.entries.firstOrNull { (name, _) ->
                    name.equals("Content-Length", ignoreCase = true)
                }?.value?.toLongOrNull()?.let { declaredLength ->
                    if (declaredLength > request.maximumBodyBytes) {
                        throw IOException("response body exceeds configured limit")
                    }
                }
                val body = when (status) {
                    HttpURLConnection.HTTP_NOT_MODIFIED, HttpURLConnection.HTTP_NO_CONTENT ->
                        byteArrayOf()
                    in 200..399 -> connection.inputStream.readBounded(request.maximumBodyBytes)
                    else -> connection.errorStream?.readBounded(request.maximumBodyBytes) ?: byteArrayOf()
                }
                SyncHttpResponse(statusCode = status, headers = headers, body = body)
            } finally {
                connection.disconnect()
            }
        }

    private fun validateRequest(request: SyncHttpRequest) {
        val uri = try {
            URI(request.url)
        } catch (error: Exception) {
            throw IOException("sync URL is invalid", error)
        }
        if (uri.scheme != "https" || uri.host == null || uri.userInfo != null || uri.fragment != null) {
            throw IOException("sync URL must be absolute HTTPS")
        }
        if (request.method !in setOf("GET", "POST")) {
            throw IOException("sync HTTP method is invalid")
        }
        if (request.bearerToken.isNotEmpty() &&
            (request.bearerToken.length !in 16..4096 || request.bearerToken.any { it == '\r' || it == '\n' })
        ) {
            throw IOException("device credential is invalid")
        }
        if (request.maximumBodyBytes !in 1..5 * 1024 * 1024) {
            throw IOException("response body limit is invalid")
        }
        request.ifNoneMatch?.let { etag ->
            if (etag.any { it == '\r' || it == '\n' }) throw IOException("ETag is invalid")
        }
        if ((request.method == "GET") != (request.body == null)) {
            throw IOException("HTTP method and body are inconsistent")
        }
        if (request.body != null && request.contentType != "application/json") {
            throw IOException("pairing request content type is invalid")
        }
    }
}

private fun InputStream.readBounded(maximumBytes: Int): ByteArray = use { input ->
    val output = ByteArrayOutputStream(minOf(maximumBytes, 16 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maximumBytes) throw IOException("response body exceeds configured limit")
        output.write(buffer, 0, read)
    }
    output.toByteArray()
}
