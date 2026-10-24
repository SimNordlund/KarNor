package com.example.karnor.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI

@Component
class SiteUrls(
    @Value("\${APP_URL:http://localhost:5173}") appUrl: String,
    @Value("\${API_PUBLIC_URL:}") apiUrl: String,
    @Value("\${RENDER_EXTERNAL_URL:}") renderUrl: String,
) {
    val frontendOrigin = origin(appUrl)
    val apiOrigin = origin(apiUrl.ifBlank { renderUrl }.ifBlank { frontendOrigin })
    val secureCookies = apiOrigin.startsWith("https:")
    val crossOriginCookies = secureCookies && apiOrigin != frontendOrigin

    private fun origin(value: String): String {
        val uri = URI.create(value.trim())
        require(uri.scheme in setOf("http", "https") && uri.host != null && uri.rawUserInfo == null
            && uri.rawQuery == null && uri.rawFragment == null && uri.path.orEmpty() in setOf("", "/")) {
            "APP_URL and API_PUBLIC_URL must be HTTP(S) origins without a path, query or credentials."
        }
        val port = uri.port.takeUnless { (uri.scheme == "https" && it == 443) || (uri.scheme == "http" && it == 80) } ?: -1
        return URI(uri.scheme, null, uri.host, port, null, null, null).toString()
    }
}
