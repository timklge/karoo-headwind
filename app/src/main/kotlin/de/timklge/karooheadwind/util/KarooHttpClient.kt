package de.timklge.karooheadwind.util

import android.util.Log
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.weatherprovider.WeatherProviderException
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.time.Duration.Companion.seconds

/**
 * OkHttp [Interceptor] that routes all HTTP requests through the Karoo system service
 * for tunneling via companion app.
 */
class KarooHttpInterceptor(private val karooSystemService: KarooSystemService) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val url = request.url.toString()
        val headers = request.headers.toMap()
        val body = request.body?.let { requestBody ->
            val buffer = okio.Buffer()
            requestBody.writeTo(buffer)
            buffer.readByteArray()
        }

        Log.d(KarooHeadwindExtension.TAG, "KarooHttpInterceptor: ${request.method} $url")

        val complete = runBlocking {
            karooHttpRequest(karooSystemService, request.method, url, headers, body)
        }

        val mediaType = complete.headers["content-type"]?.toMediaTypeOrNull()
        val responseBody = (complete.body ?: ByteArray(0)).toResponseBody(mediaType)

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(complete.statusCode)
            .message(complete.error ?: "")
            .apply {
                complete.headers.forEach { (name, value) -> addHeader(name, value) }
            }
            .body(responseBody)
            .build()
    }
}

/**
 * Suspend function that performs an HTTP request via the Karoo system service and returns
 * the raw [HttpResponseState.Complete]. Throws [WeatherProviderException] on network error
 * and emits a synthetic 500 response on timeout.
 */
@OptIn(FlowPreview::class)
suspend fun karooHttpRequest(
    karooSystemService: KarooSystemService,
    method: String,
    url: String,
    headers: Map<String, String> = mapOf(),
    body: ByteArray? = null,
    timeoutSeconds: Int = 30,
): HttpResponseState.Complete {
    return callbackFlow {
        Log.i(KarooHeadwindExtension.TAG, "KarooHttpClient: making request $method $url with headers $headers and body ${body?.size ?: 0} bytes")

        val listenerId = karooSystemService.addConsumer(
            OnHttpResponse.MakeHttpRequest(
                method = method,
                url = url,
                waitForConnection = false,
                headers = headers + mapOf("User-Agent" to KarooHeadwindExtension.TAG),
                body = body,
            ),
            onEvent = { event: OnHttpResponse ->
                if (event.state is HttpResponseState.Complete) {
                    Log.d(KarooHeadwindExtension.TAG, "KarooHttpClient: response received for $url")
                    trySend(event.state as HttpResponseState.Complete)
                    close()
                }
            },
            onError = { err ->
                Log.e(KarooHeadwindExtension.TAG, "KarooHttpClient: error for $url: $err")
                close(WeatherProviderException(0, "Http error: $err"))
            }
        )
        awaitClose { karooSystemService.removeConsumer(listenerId) }
    }
        .timeout(timeoutSeconds.seconds)
        .catch { e ->
            emit(HttpResponseState.Complete(500, mapOf(), null, e.message))
            throw e
        }
        .single()
}

/**
 * Builds an [OkHttpClient] that routes all HTTP requests through the Karoo system service.
 */
fun buildKarooOkHttpClient(karooSystemService: KarooSystemService): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor(KarooHttpInterceptor(karooSystemService))
        .build()
