package com.briefy.api.config

import com.briefy.api.api.ErrorResponse
import com.briefy.api.infrastructure.logging.RequestMdcFilter
import com.briefy.api.infrastructure.security.JwtAuthenticationFilter
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
import java.time.Instant

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val requestMdcFilter: RequestMdcFilter,
    private val objectMapper: ObjectMapper
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/error").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/signup").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                    .requestMatchers("/api/health").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
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
