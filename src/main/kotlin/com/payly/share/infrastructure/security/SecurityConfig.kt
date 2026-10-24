package com.payly.share.infrastructure.security

import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val apiKeyFilter: ApiKeyFilter,
) {

    /**
     * Цепочка для эндпоинтов Actuator — проверяется раньше основной (@Order(1)).
     *
     * Health и info открыты всегда: их дёргают пробы оркестратора и балансер.
     * Остальное (prometheus, metrics) открыто, только если Actuator вынесен
     * на отдельный внутренний порт (management.server.port) — если кто-то
     * вернёт его на общий порт API, эти эндпоинты закроются, а не утекут наружу.
     */
    @Bean
    @Order(1)
    fun actuatorSecurityFilterChain(http: HttpSecurity, environment: Environment): SecurityFilterChain {
        val isolatedManagementPort = ManagementPortType.get(environment) == ManagementPortType.DIFFERENT

        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                if (isolatedManagementPort) it.anyRequest().permitAll() else it.anyRequest().denyAll()
            }

        return http.build()
    }

    @Bean
    @Order(2)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/auth/**").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
