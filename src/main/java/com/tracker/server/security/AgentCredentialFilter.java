package com.tracker.server.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tracker.server.agent.service.AgentCredentialService;
import com.tracker.server.entity.User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AgentCredentialFilter extends OncePerRequestFilter {

    public static final String DEVICE_HEADER = "X-Agent-Device";
    public static final String TOKEN_HEADER = "X-Agent-Token";
    private static final String AGENT_PREFIX = "/api/v1/agent/";
    private static final String ENROLL_PATH = "/api/v1/agent/devices/enroll";

    private final AgentCredentialService credentialService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(AGENT_PREFIX) || path.equals(ENROLL_PATH);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String deviceUuid = request.getHeader(DEVICE_HEADER);
        String token = request.getHeader(TOKEN_HEADER);
        if (deviceUuid == null && token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (!deviceUuid.equalsIgnoreCase(pathDeviceUuid(request.getRequestURI()))) {
                throw new IllegalArgumentException("Device credential does not match path");
            }
            User user = credentialService.authenticate(deviceUuid, token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user.getUsername(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String pathDeviceUuid(String path) {
        String marker = "/devices/";
        int start = path.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = path.indexOf('/', start);
        return end < 0 ? path.substring(start) : path.substring(start, end);
    }
}
