package com.briefy.api.infrastructure.mcp

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class McpSecurityConfig(
    private val mcpAuthFilter: McpAuthFilter,
    @Value("\${mcp.cors.allowed-origins:http://localhost:6274}") private val allowedOriginsCsv: String,
) {

    @Bean
    @Order(1)
    fun mcpSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/mcp/**")
            .cors { it.configurationSource(mcpCorsSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.OPTIONS, "/mcp/**").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(mcpAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.setHeader("WWW-Authenticate", "Bearer realm=\"briefy-mcp\"")
                    response.status = 401
                }
            }
        return http.build()
    }

    @Bean
    fun mcpAuthFilterRegistration(filter: McpAuthFilter): FilterRegistrationBean<McpAuthFilter> {
        return FilterRegistrationBean(filter).apply { isEnabled = false }
    }

    private fun mcpCorsSource(): CorsConfigurationSource {
        val origins = allowedOriginsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val cfg = CorsConfiguration().apply {
            allowedOrigins = origins
            allowedMethods = listOf("GET", "POST", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "Accept", "Mcp-Session-Id", "Last-Event-ID")
            exposedHeaders = listOf("Mcp-Session-Id", "WWW-Authenticate")
            allowCredentials = false
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/mcp/**", cfg)
        }
    }
}
