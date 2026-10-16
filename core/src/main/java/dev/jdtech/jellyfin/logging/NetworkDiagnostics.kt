package dev.jdtech.jellyfin.logging

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import timber.log.Timber

object NetworkDiagnostics : Interceptor {
    val factory by lazy { OkHttpFactory(OkHttpClient.Builder().addInterceptor(this).build()) }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = System.nanoTime()
        try {
            val response = chain.proceed(request)
            val duration = (System.nanoTime() - started) / 1_000_000
            if (!response.isSuccessful) {
                Timber.w(
                    "API %s host=%s status=%d duration=%d ms",
                    request.method,
                    request.url.host,
                    response.code,
                    duration,
                )
            } else {
                Timber.d(
                    "API %s host=%s status=%d duration=%d ms",
                    request.method,
                    request.url.host,
                    response.code,
                    duration,
                )
            }
            return response
        } catch (e: IOException) {
            Timber.w(
                "API %s host=%s failed=%s duration=%d ms",
                request.method,
                request.url.host,
                e.javaClass.simpleName,
                (System.nanoTime() - started) / 1_000_000,
            )
            throw e
        }
    }
}
