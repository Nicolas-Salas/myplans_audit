package com.myplans.audit.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

        public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

        private final JwtUtil jwtUtil;

        @Value("${audit.internal.token:}")
        private String internalToken;

        @Override
        protected void doFilterInternal(
                        @NonNull HttpServletRequest request,
                        @NonNull HttpServletResponse response,
                        @NonNull FilterChain filterChain) throws ServletException, IOException {
                String internalHeader = request.getHeader(INTERNAL_TOKEN_HEADER);
                if (internalHeader != null && !internalHeader.isBlank()
                                && internalToken != null && !internalToken.isBlank()
                                && internalToken.equals(internalHeader)) {

                        AuthenticatedUser principal = AuthenticatedUser.builder()
                                        .idUsuario(null)
                                        .email("internal-service@internal")
                                        .roles(List.of("ROLE_CORE_SERVICE", "ROLE_REPORTS_SERVICE", "ROLE_ADMIN"))
                                        .build();

                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                        principal, null,
                                        List.of(
                                                        new SimpleGrantedAuthority("ROLE_CORE_SERVICE"),
                                                        new SimpleGrantedAuthority("ROLE_REPORTS_SERVICE"),
                                                        new SimpleGrantedAuthority("ROLE_ADMIN")));
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        filterChain.doFilter(request, response);
                        return;
                }

                String authHeader = request.getHeader("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        filterChain.doFilter(request, response);
                        return;
                }

                String jwt = authHeader.substring(7);
                try {
                        if (jwtUtil.isTokenValid(jwt)
                                        && SecurityContextHolder.getContext().getAuthentication() == null) {

                                String email = jwtUtil.extractUsername(jwt);
                                Integer idUsuario = jwtUtil.extractUserId(jwt);
                                List<String> roles = jwtUtil.extractRoles(jwt);

                                AuthenticatedUser principal = AuthenticatedUser.builder()
                                                .idUsuario(idUsuario)
                                                .email(email)
                                                .roles(roles)
                                                .build();

                                List<SimpleGrantedAuthority> authorities = roles.stream()
                                                .map(SimpleGrantedAuthority::new)
                                                .collect(Collectors.toList());

                                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                                principal, null, authorities);
                                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                } catch (ExpiredJwtException ex) {
                        request.setAttribute("jwt_error_message",
                                        "Tu sesión ha expirado. Por favor inicia sesión nuevamente");
                        logger.warn("JWT expirado: " + ex.getMessage());
                } catch (SignatureException | MalformedJwtException | UnsupportedJwtException ex) {
                        request.setAttribute("jwt_error_message",
                                        "Token inválido. Por favor inicia sesión nuevamente");
                        logger.warn("JWT inválido: " + ex.getMessage());
                } catch (JwtException ex) {
                        request.setAttribute("jwt_error_message",
                                        "No se pudo validar tu sesión. Por favor inicia sesión nuevamente");
                        logger.warn("JwtException: " + ex.getMessage());
                } catch (Exception ex) {
                        request.setAttribute("jwt_error_message",
                                        "No se pudo validar tu sesión. Por favor inicia sesión nuevamente");
                        logger.error("Error inesperado validando JWT: " + ex.getMessage());
                }

                filterChain.doFilter(request, response);
        }
}