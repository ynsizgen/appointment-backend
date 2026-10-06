package com.appointmentplatform.identity.security;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.core.userdetails.*;

@Configuration(proxyBeanMethods=false)
public class IdentitySecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
    // JSON login is handled by IdentityService. Suppress generated default users.
    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Unavailable");
        };
    }
}
