package com.briefy.api.infrastructure.mcp

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class McpSecurityConfig(private val mcpAuthFilter: McpAuthFilter) {

    @Bean
    @Order(1)
    fun mcpSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/mcp/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
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
}
