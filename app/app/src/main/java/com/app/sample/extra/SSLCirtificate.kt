package com.app.sample.extra

import android.annotation.SuppressLint
import okhttp3.OkHttpClient
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


@SuppressLint("CustomX509TrustManager")
class TrustAllCertificates : X509TrustManager {
    override fun checkClientTrusted(
        p0: Array<out java.security.cert.X509Certificate>?,
        authType: String?
    ) {
        // No implementation needed
    }

    override fun checkServerTrusted(
        p0: Array<out java.security.cert.X509Certificate>?,
        authType: String?
    ) {
        // No implementation needed
    }

    override fun getAcceptedIssuers(): Array<out java.security.cert.X509Certificate>? {
        return arrayOf()
    }
}

fun createUnsafeOkHttpClient(): OkHttpClient {

    val trustAllCerts = arrayOf<TrustManager>(TrustAllCertificates())
    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, java.security.SecureRandom())

    val sslSocketFactory = sslContext.socketFactory
    val hostnameVerifier = HostnameVerifier { _, _ -> true }

    return OkHttpClient.Builder()
        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier(hostnameVerifier)
        .build()
}
