package com.logistics.order.config;

import com.logistics.common.security.JwtTokenProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SecurityConfig {

    @Produces
    @ApplicationScoped
    public JwtTokenProvider jwtTokenProvider(
            @ConfigProperty(name = "security.jwt.secret", defaultValue = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970") String secret,
            @ConfigProperty(name = "security.jwt.expiration-ms", defaultValue = "86400000") long expirationMs,
            @ConfigProperty(name = "security.jwt.issuer", defaultValue = "logistics-platform") String issuer
    ) {
        return new JwtTokenProvider(secret, expirationMs, issuer);
    }
}
