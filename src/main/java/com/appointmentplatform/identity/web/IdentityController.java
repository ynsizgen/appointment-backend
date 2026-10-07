package com.appointmentplatform.identity.web;

import com.appointmentplatform.identity.application.*;
import com.appointmentplatform.identity.security.AccountPrincipal;
import com.appointmentplatform.identity.web.dto.*;

import com.appointmentplatform.shared.RateLimits;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class IdentityController {

    private final IdentityService service;
    private final RateLimits limits;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    private final Clock clock;

    public IdentityController(IdentityService service, RateLimits limits,
                              SecurityContextRepository contexts, SessionAuthenticationStrategy sessions, Clock clock) {
        this.service=service;
        this.limits=limits;
        this.contexts=contexts;
        this.sessions=sessions;
        this.clock=clock;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/registrations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse register(@Valid @RequestBody RegistrationRequest body, HttpServletRequest request) {
        limits.check("registration-ip",request.getRemoteAddr(),10,3600);
        limits.check("registration-email",IdentityService.normalizeEmail(body.email()),3,3600);
        service.register(new RegistrationCommand(body.email(), body.password(), body.firstName(), body.lastName(), body.phone(), body.displayName(), body.address(), body.googleMapsUrl()));
        return new MessageResponse("Adres uygunsa doğrulama e-postası gönderilecektir.");
    }
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerificationRequest body, HttpServletRequest request) {
        limits.check("verification-ip",request.getRemoteAddr(),30,900);
        service.verify(body.token());
    }

    @PostMapping("/forgot-brand-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotBrandPassword(@Valid @RequestBody ForgotRequest body, HttpServletRequest request) {
        limits.check("password-reset-ip",request.getRemoteAddr(),10,3600);
        limits.check("password-reset-email",IdentityService.normalizeEmail(body.email()),3,3600);
        service.forgotBrandPassword(body.email());
        return new MessageResponse("Adres uygunsa parola sıfırlama e-postası gönderilecektir.");
    }

    @PostMapping("/reset-brand-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetBrandPassword(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
        limits.check("password-reset-confirm-ip",request.getRemoteAddr(),30,900);
        service.resetBrandPassword(body.token(), body.password());
    }

    @PostMapping("/login")
    public AccountResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        limits.check("login-ip",request.getRemoteAddr(),60,900);
        limits.check("login-email",IdentityService.normalizeEmail(body.email()),10,900);
        var user=service.login(new LoginCommand(body.email(), body.password(), body.mfaCode()));
        var principal=new AccountPrincipal(user.id(),user.role(),user.brandId(),clock.instant());
        var authentication=UsernamePasswordAuthenticationToken.authenticated(principal,null,AuthorityUtils.createAuthorityList("ROLE_"+user.role()));
        sessions.onAuthentication(authentication,request,response);
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context,request,response);
        return AccountResponse.from(user);
    }
    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return AccountResponse.from(service.findAccount(principal.id()).orElseThrow());
    }
}
