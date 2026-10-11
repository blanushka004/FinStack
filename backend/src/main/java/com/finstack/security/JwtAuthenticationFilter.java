package com.finstack.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    public static final String INVALID_TOKEN_ATTRIBUTE = "finstack.invalidJwt";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<String> email = jwtService.extractSubject(token);
            if (email.isPresent()) {
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email.get());
                    boolean currentVersion = userDetails instanceof CustomUserDetailsService.VersionedUserDetails details
                            && jwtService.matchesTokenVersion(token, details.getTokenVersion());
                    if (userDetails.isEnabled() && currentVersion) {
                        UsernamePasswordAuthenticationToken authentication =
                                UsernamePasswordAuthenticationToken.authenticated(
                                        userDetails, null, userDetails.getAuthorities()
                                );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
                    }
                } catch (UsernameNotFoundException exception) {
                    SecurityContextHolder.clearContext();
                    request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
                }
            } else {
                request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
            }
        } else if (token.isEmpty()) {
            request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
        }

        filterChain.doFilter(request, response);
    }
}
