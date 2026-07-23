package com.tracker.server;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tracker.server.security.JwtFilter;
import com.tracker.server.security.AgentCredentialFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final AgentCredentialFilter agentCredentialFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> { })
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(401)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.ico",
                                "/error").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/admin").permitAll()
                        .requestMatchers("/api/auth/admin/register").hasRole("ADMIN")
                        .requestMatchers("/api/auth/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/agent/**").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/process/my-processes",
                                "/api/process/my-sessions",
                                "/api/process/my-productivity").authenticated()
                        .requestMatchers("/api/user/**").authenticated()
                        .requestMatchers(
                                "/api/admin/**",
                                "/api/device/**",
                                "/api/process/**",
                                "/api/window/**",
                                "/api/idle/**",
                                "/api/session/**",
                                "/api/test/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable());

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(agentCredentialFilter, JwtFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "https://windows-tracker-dashboard.vercel.app"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
