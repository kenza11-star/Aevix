package app.absentmotion.library

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

enum class PresetSourceProblem { Empty, TooLarge, Invalid, Insecure, NotDownload, DownloadTooLarge, Network }
class PresetSourceException(val problem: PresetSourceProblem) : IllegalArgumentException(problem.name)

sealed interface PresetSource {
    data class Xml(val text: String) : PresetSource
    data class Download(val url: URI) : PresetSource {
        fun download(directory: File, checkActive: () -> Unit = {}): File {
            directory.mkdirs()
            val file = File.createTempFile("preset-", ".download", directory)
            var ready = false
            try {
                var address = url
                for (redirect in 0..5) {
                    checkActive()
                    val connection = address.toURL().openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 15_000
                        connection.readTimeout = 20_000
                        connection.instanceFollowRedirects = false
                        connection.setRequestProperty("Accept", "application/xml, text/xml, application/zip, application/octet-stream")
                        val code = connection.responseCode
                        if (code in setOf(301, 302, 303, 307, 308)) {
                            val location = connection.getHeaderField("Location") ?: throw PresetSourceException(PresetSourceProblem.NotDownload)
                            address = (parse(address.resolve(location).toString()) as? Download)?.url
                                ?: throw PresetSourceException(PresetSourceProblem.Insecure)
                            continue
                        }
                        if (code !in 200..299) throw PresetSourceException(PresetSourceProblem.Network)
                        val mime = connection.contentType.orEmpty().substringBefore(';').trim().lowercase(java.util.Locale.ROOT)
                        if (mime in setOf("text/html", "application/xhtml+xml")) throw PresetSourceException(PresetSourceProblem.NotDownload)
                        val limit = 256L shl 20
                        if (connection.contentLengthLong > limit) throw PresetSourceException(PresetSourceProblem.DownloadTooLarge)
                        connection.inputStream.use { input -> file.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var total = 0L
                            while (true) {
                                checkActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > limit) throw PresetSourceException(PresetSourceProblem.DownloadTooLarge)
                                output.write(buffer, 0, count)
                            }
                        } }
                        checkActive()
                        if (file.length() == 0L) throw PresetSourceException(PresetSourceProblem.NotDownload)
                        ready = true
                        return file
                    } finally {
                        connection.disconnect()
                    }
                }
                throw PresetSourceException(PresetSourceProblem.NotDownload)
            } catch (_: IOException) {
                throw PresetSourceException(PresetSourceProblem.Network)
            } finally {
                if (!ready) file.delete()
            }
        }
    }

    companion object {
        fun parse(value: String): PresetSource {
            if (value.length > 16 shl 20) throw PresetSourceException(PresetSourceProblem.TooLarge)
            var text = value.trim().removePrefix("\uFEFF").trim()
            if (text.startsWith("```") && text.endsWith("```") && '\n' in text) {
                text = text.removeSuffix("```").substringAfter('\n').trim()
            }
            if (text.isEmpty()) throw PresetSourceException(PresetSourceProblem.Empty)
            if (text.startsWith('<')) return Xml(text)
            val parsed = runCatching { URI(text) }.getOrNull() ?: throw PresetSourceException(PresetSourceProblem.Invalid)
            if (parsed.scheme.equals("http", true) && parsed.host.equals("alight.link", true) && parsed.rawUserInfo == null) {
                return parse("https" + text.substring(text.indexOf(':')))
            }
            if (!parsed.scheme.equals("https", true) || parsed.host.isNullOrBlank() || parsed.rawUserInfo != null) {
                throw PresetSourceException(if (parsed.scheme != null) PresetSourceProblem.Insecure else PresetSourceProblem.Invalid)
            }
            return Download(parsed)
        }
    }
}
