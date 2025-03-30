package de.timklge.karooheadwind.util

import io.hammerhead.karooext.models.HttpResponseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.GZIPInputStream

suspend fun ungzip(response: HttpResponseState.Complete): String {
    val inputStream = java.io.ByteArrayInputStream(response.body ?: ByteArray(0))
    val lowercaseHeaders = response.headers.map { (k: String, v: String) -> k.lowercase() to v.lowercase() }.toMap()
    val isGzippedResponse = lowercaseHeaders["content-encoding"]?.contains("gzip") == true

    return if(isGzippedResponse){
        val gzipStream = withContext(Dispatchers.IO) { GZIPInputStream(inputStream) }
        gzipStream.use { stream -> String(stream.readBytes()) }
    } else {
        inputStream.use { stream -> String(stream.readBytes()) }
    }
}