package com.appointmentplatform.identity.security;

import com.appointmentplatform.identity.application.IdentityService;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.Objects;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionGuard extends OncePerRequestFilter {

    private final IdentityService users;
    private final Clock clock;
    private final Duration maxAge;

    public SessionGuard(IdentityService users,Clock clock,Duration maxAge) {
        this.users=users;
        this.clock=clock;
        this.maxAge=maxAge;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            var current = users.findAccount(principal.id()).orElse(null);
            if (current==null || !current.enabled() || !current.role().equals(principal.role())
                || !Objects.equals(current.brandId(),principal.brandId())
                || (current.sessionsRevokedAt() != null && !principal.authenticatedAt().isAfter(current.sessionsRevokedAt()))
                || (current.passwordChangedAt() != null && !principal.authenticatedAt().isAfter(current.passwordChangedAt()))
                || !principal.authenticatedAt().plus(maxAge).isAfter(clock.instant())) {
                if(request.getSession(false) != null)
                    request.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request,response);
    }
}
