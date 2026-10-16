package com.appointmentplatform.identity.security;

import com.appointmentplatform.identity.application.IdentityService;
import com.appointmentplatform.audit.Audit;
import jakarta.servlet.DispatcherType;
import java.time.*;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.*;

@Configuration(proxyBeanMethods=false)
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityContextRepository contexts() {
        return new HttpSessionSecurityContextRepository();
    }
    @Bean
    CsrfTokenRepository csrfTokens() {
        return new HttpSessionCsrfTokenRepository();
    }
    @Bean
    SessionAuthenticationStrategy sessions(CsrfTokenRepository tokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),new CsrfAuthenticationStrategy(tokens)));
    }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,SecurityContextRepository contexts,CsrfTokenRepository tokens,
        IdentityService users,Clock clock,Audit audit,@Value("${app.security.session-max-age}") Duration maxAge) throws Exception {
        http.securityContext(c->c.securityContextRepository(contexts));
        http.csrf(c->c.csrfTokenRepository(tokens));
        http.requestCache(c->c.disable());
        http.authorizeHttpRequests(r->r
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers(HttpMethod.GET,"/actuator/health","/api/auth/csrf","/verify-email","/verify-email.js","/reset-password","/reset-password.js").permitAll()
            .requestMatchers(HttpMethod.POST,"/api/auth/registrations","/api/auth/verify-email","/api/auth/login","/api/auth/forgot-brand-password","/api/auth/reset-brand-password").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/public/appointment/create/*", "/api/public/phone-verifications/request", "/api/public/phone-verifications/verify").permitAll()
            .requestMatchers(HttpMethod.GET,"/api/auth/me").authenticated()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/panel/**").hasRole("BRAND")
            .anyRequest().denyAll());
        http.addFilterAfter(new SessionGuard(users,clock,maxAge),SecurityContextHolderFilter.class);
        http.headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'none'; script-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'")));
        http.logout(l->l.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
            .addLogoutHandler((request,response,authentication)->{
                if(authentication!=null && authentication.getPrincipal() instanceof AccountPrincipal p)
                    audit.record(p.id(),p.brandId(),"LOGOUT","SUCCESS");
            })
            .logoutSuccessHandler((request,response,authentication)->response.setStatus(204)));
        http.exceptionHandling(e->e
            .authenticationEntryPoint((request,response,exception)->{
                writeProblem(response, 401, "Kimlik doğrulama gerekli",
                    "Bu kaynağa erişmek için geçerli bir oturumla giriş yapmalısınız.");
            })
            .accessDeniedHandler((request,response,exception)->{
                var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                exception.printStackTrace();
                audit.failure(auth != null && auth.getPrincipal() instanceof AccountPrincipal p?p.id():null,"ACCESS");
                if (exception instanceof MissingCsrfTokenException) {
                    writeProblem(response, 403, "CSRF token eksik",
                        "Önce GET /api/auth/csrf isteğiyle token alın; aynı oturum çerezini koruyup dönen headerName başlığına token değerini yazarak isteği tekrar gönderin.");
                } else if (exception instanceof InvalidCsrfTokenException) {
                    writeProblem(response, 403, "CSRF token geçersiz",
                        "CSRF token bu oturumla eşleşmiyor veya artık geçerli değil. GET /api/auth/csrf ile yeni token alıp aynı oturum çereziyle tekrar deneyin.");
                } else {
                    writeProblem(response, 403, "Erişim reddedildi",
                        "Bu işlemi gerçekleştirmek için gerekli yetkiye sahip değilsiniz.");
                }
            }
            ));
        return http.build();
    }

    private static void writeProblem(HttpServletResponse response, int status, String title, String detail)
            throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title
                + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
