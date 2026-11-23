package com.logistics.user.config;

import com.logistics.common.security.JwtTokenProvider;
import com.logistics.common.security.ServiceTokenManager;
import com.logistics.common.security.ServiceTokenValidator;
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

    @Produces
    @ApplicationScoped
    public ServiceTokenManager serviceTokenManager(
            @ConfigProperty(name = "security.service.id", defaultValue = "user-service") String serviceId,
            @ConfigProperty(name = "security.jwt.service.secret", defaultValue = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970") String secret,
            @ConfigProperty(name = "security.jwt.issuer", defaultValue = "logistics-platform") String issuer
    ) {
        return new ServiceTokenManager(serviceId, secret, issuer, 300L, 30L);
    }

    @Produces
    @ApplicationScoped
    public ServiceTokenValidator serviceTokenValidator(
            @ConfigProperty(name = "security.service.id", defaultValue = "user-service") String serviceId,
            @ConfigProperty(name = "security.jwt.service.secret", defaultValue = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970") String secret,
            @ConfigProperty(name = "security.jwt.service.rotation-secret", defaultValue = "") String rotationSecret,
            @ConfigProperty(name = "security.jwt.issuer", defaultValue = "logistics-platform") String issuer
    ) {
        return new ServiceTokenValidator(serviceId, secret, rotationSecret.isBlank() ? null : rotationSecret, issuer);
    }
}
