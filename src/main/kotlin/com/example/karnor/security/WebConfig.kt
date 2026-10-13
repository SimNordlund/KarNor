package com.example.karnor.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.net.URI

@Configuration
open class WebConfig(
    @Value("\${APP_URL:http://localhost:5173}") appUrl: String,
) : WebMvcConfigurer {
    private val frontendOrigin = URI.create(appUrl).let {
        "${it.scheme}://${it.host}${if (it.port >= 0) ":${it.port}" else ""}"
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(frontendOrigin)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}
