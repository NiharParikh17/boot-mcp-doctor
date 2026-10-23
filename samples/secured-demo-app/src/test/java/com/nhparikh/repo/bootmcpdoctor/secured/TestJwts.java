package com.nhparikh.repo.bootmcpdoctor.secured;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Plays the identity provider in tests: signs JWT access tokens with a key generated for the test run, so no key is
 * ever stored in the repository. Import {@link Decoder} to make the application accept them.
 */
final class TestJwts {

    private static final KeyPair KEYS = generate();

    private static final KeyPair OTHER_KEYS = generate();

    private TestJwts() {
    }

    /**
     * @param subject the {@code sub} claim, which becomes the caller's name
     * @param scopes the scopes, which become {@code SCOPE_<scope>} authorities
     * @return a token that is valid for five minutes
     */
    static String token(String subject, String... scopes) {
        Instant now = Instant.now();
        return sign(KEYS, subject, now, now.plus(Duration.ofMinutes(5)), scopes);
    }

    static String expiredToken(String subject, String... scopes) {
        Instant now = Instant.now();
        return sign(KEYS, subject, now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), scopes);
    }

    static String tokenSignedByAnotherKey(String subject, String... scopes) {
        Instant now = Instant.now();
        return sign(OTHER_KEYS, subject, now, now.plus(Duration.ofMinutes(5)), scopes);
    }

    private static String sign(KeyPair keys, String subject, Instant issuedAt, Instant expiresAt, String... scopes) {
        RSAKey key = new RSAKey.Builder((RSAPublicKey) keys.getPublic()).privateKey(keys.getPrivate()).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().subject(subject).issuedAt(issuedAt).expiresAt(expiresAt);
        if (scopes.length > 0) {
            claims.claim("scope", String.join(" ", scopes));
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Makes the application accept the tokens from {@link TestJwts}, as
     * {@code spring.security.oauth2.resourceserver.jwt.*} would for a real identity provider.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class Decoder {

        @Bean
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        }
    }
}
