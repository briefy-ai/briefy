package com.briefy.api.config

import com.briefy.api.api.ErrorResponse
import com.briefy.api.infrastructure.logging.RequestMdcFilter
import com.briefy.api.infrastructure.security.JwtAuthenticationFilter
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.time.Instant

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val requestMdcFilter: RequestMdcFilter,
    private val objectMapper: ObjectMapper,
    @Value("\${mcp.cors.allowed-origins:http://localhost:6274,http://localhost:3000}") private val mcpCorsOriginsCsv: String,
) {

    @Bean
    @Order(2)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(oauthCorsSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/error").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/signup").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/integrations/telegram/webhook").permitAll()
                    .requestMatchers("/api/health").permitAll()
                    .requestMatchers("/api/public/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/oauth/authorize", "/oauth/token", "/oauth/revoke").permitAll()
                    .requestMatchers("/authorize", "/token", "/revoke").permitAll()
                    .requestMatchers("/.well-known/oauth-authorization-server").permitAll()
                    .requestMatchers("/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/oauth/register", "/register").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required")
                }
                it.accessDeniedHandler { _, response, _ ->
                    writeError(response, HttpStatus.FORBIDDEN, "Access denied")
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(requestMdcFilter, JwtAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    private fun oauthCorsSource(): CorsConfigurationSource {
        val origins = mcpCorsOriginsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val cfg = CorsConfiguration().apply {
            allowedOrigins = origins
            allowedMethods = listOf("GET", "POST", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "Accept")
            allowCredentials = false
            maxAge = 3600L
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/.well-known/**", cfg)
        source.registerCorsConfiguration("/oauth/**", cfg)
        return source
    }

    private fun writeError(response: HttpServletResponse, status: HttpStatus, message: String) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val payload = ErrorResponse(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            timestamp = Instant.now()
        )
        response.writer.write(objectMapper.writeValueAsString(payload))
    }
}
