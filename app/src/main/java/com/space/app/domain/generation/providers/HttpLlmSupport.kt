package com.space.app.domain.generation.providers

import com.space.app.domain.generation.LlmResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

fun defaultHttpClient(timeoutSeconds: Long = 20L): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(timeoutSeconds + 5, TimeUnit.SECONDS)
        .build()

suspend fun OkHttpClient.executeLlm(request: Request): LlmResult =
    withContext(Dispatchers.IO) {
        try {
            newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    LlmResult.Ok(body)
                } else {
                    LlmResult.HttpError(
                        code = response.code,
                        body = body,
                        retryAfterSeconds = parseRetryAfter(response)
                    )
                }
            }
        } catch (_: SocketTimeoutException) {
            LlmResult.Timeout
        } catch (error: IOException) {
            LlmResult.NetworkError(error)
        }
    }

private fun parseRetryAfter(response: Response): Long? {
    val header = response.header("Retry-After") ?: return null
    return header.toLongOrNull()
}
