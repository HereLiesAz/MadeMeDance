package com.hereliesaz.mademedance.identify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

sealed interface AcrOutcome {
    data class Success(val artist: String?, val title: String) : AcrOutcome
    data object NoMatch : AcrOutcome
    data object NotConfigured : AcrOutcome
    data class Error(val message: String) : AcrOutcome
}

/**
 * Recognizes a recorded audio clip against ACRCloud's identify API (free
 * developer tier). Unlike the live-listening recognizer apps, this works on
 * the saved clip — the whole point being to identify a song after it has
 * stopped playing.
 *
 * Credentials are the user's own (host / access key / access secret), entered
 * in Settings; we ship none.
 */
object AcrCloudClient {

    suspend fun recognize(
        host: String,
        accessKey: String,
        accessSecret: String,
        audio: ByteArray
    ): AcrOutcome = withContext(Dispatchers.IO) {
        if (host.isBlank() || accessKey.isBlank() || accessSecret.isBlank()) {
            return@withContext AcrOutcome.NotConfigured
        }
        try {
            val endpoint = "https://$host/v1/identify"
            val dataType = "audio"
            val signatureVersion = "1"
            val timestamp = (System.currentTimeMillis() / 1000).toString()

            val stringToSign =
                "POST\n/v1/identify\n$accessKey\n$dataType\n$signatureVersion\n$timestamp"
            val signature = hmacSha1Base64(stringToSign, accessSecret)

            val boundary = "----mmdBoundary${System.currentTimeMillis()}"
            val body = buildMultipart(
                boundary = boundary,
                fields = linkedMapOf(
                    "access_key" to accessKey,
                    "data_type" to dataType,
                    "signature_version" to signatureVersion,
                    "signature" to signature,
                    "sample_bytes" to audio.size.toString(),
                    "timestamp" to timestamp
                ),
                fileFieldName = "sample",
                fileName = "sample.wav",
                fileBytes = audio
            )

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            conn.outputStream.use { it.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()

            parseResponse(text)
        } catch (e: Exception) {
            AcrOutcome.Error(e.message ?: "Network error")
        }
    }

    private fun parseResponse(text: String): AcrOutcome {
        if (text.isBlank()) return AcrOutcome.Error("Empty response")
        return try {
            val json = JSONObject(text)
            val status = json.optJSONObject("status")
            when (val statusCode = status?.optInt("code", -1) ?: -1) {
                0 -> {
                    val music = json.optJSONObject("metadata")?.optJSONArray("music")
                    val first = if (music != null && music.length() > 0) {
                        music.getJSONObject(0)
                    } else {
                        return AcrOutcome.NoMatch
                    }
                    val title = first.optString("title").takeIf { it.isNotBlank() }
                        ?: return AcrOutcome.NoMatch
                    val artist = first.optJSONArray("artists")
                        ?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() }
                    AcrOutcome.Success(artist, title)
                }
                1001 -> AcrOutcome.NoMatch
                else -> AcrOutcome.Error(status?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: "ACRCloud error $statusCode")
            }
        } catch (e: Exception) {
            AcrOutcome.Error("Bad response")
        }
    }

    private fun hmacSha1Base64(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val raw = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    private fun buildMultipart(
        boundary: String,
        fields: Map<String, String>,
        fileFieldName: String,
        fileName: String,
        fileBytes: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val dashes = "--"
        val crlf = "\r\n"
        for ((name, value) in fields) {
            out.write("$dashes$boundary$crlf".toByteArray())
            out.write("Content-Disposition: form-data; name=\"$name\"$crlf$crlf".toByteArray())
            out.write("$value$crlf".toByteArray())
        }
        out.write("$dashes$boundary$crlf".toByteArray())
        out.write(
            ("Content-Disposition: form-data; name=\"$fileFieldName\"; filename=\"$fileName\"$crlf")
                .toByteArray()
        )
        out.write("Content-Type: application/octet-stream$crlf$crlf".toByteArray())
        out.write(fileBytes)
        out.write(crlf.toByteArray())
        out.write("$dashes$boundary$dashes$crlf".toByteArray())
        return out.toByteArray()
    }
}
