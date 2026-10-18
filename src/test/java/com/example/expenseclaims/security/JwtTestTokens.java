package com.example.expenseclaims.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * Helpers for building JWTs in tests, including deliberately broken ones.
 * Valid tokens are signed with the application's own JwtEncoder (which uses the test profile's
 * random secret); attacker tokens use a different random key or no signature at all.
 */
public final class JwtTestTokens {

    public static final String ISSUER = "expense-claims";

    private JwtTestTokens() {
    }

    /** Signs the given claims with the application's real key. */
    public static String sign(JwtEncoder jwtEncoder, Consumer<JwtClaimsSet.Builder> claims) {
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder();
        claims.accept(builder);
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), builder.build()))
                .getTokenValue();
    }

    /** A normally-shaped token for the employee, signed with a key the application does not know. */
    public static String signedWithUnknownKey(Long employeeId) {
        byte[] otherSecret = new byte[32];
        new SecureRandom().nextBytes(otherSecret);
        JwtEncoder attackerEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(HexFormat.of().formatHex(otherSecret).getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        return sign(attackerEncoder, claims -> claims.issuer(ISSUER).subject(String.valueOf(employeeId))
                .issuedAt(now).expiresAt(now.plusSeconds(3600)));
    }

    /** An unsigned token ("alg": "none"), a classic attack on careless JWT libraries. */
    public static String unsigned(Long employeeId) {
        Instant now = Instant.now();
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"iss\":\"" + ISSUER + "\",\"sub\":\"" + employeeId + "\",\"iat\":"
                + now.getEpochSecond() + ",\"exp\":" + now.plusSeconds(3600).getEpochSecond() + "}");
        return header + "." + payload + ".";
    }

    /** Takes a valid token and changes its "sub" claim to another employee, keeping the old signature. */
    public static String withSubjectReplaced(String validToken, Long originalId, Long newId) {
        String[] parts = validToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tampered = payload.replace("\"sub\":\"" + originalId + "\"", "\"sub\":\"" + newId + "\"");
        if (tampered.equals(payload)) {
            throw new IllegalStateException("Test setup error: subject not found in token payload");
        }
        return parts[0] + "." + base64Url(tampered) + "." + parts[2];
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
