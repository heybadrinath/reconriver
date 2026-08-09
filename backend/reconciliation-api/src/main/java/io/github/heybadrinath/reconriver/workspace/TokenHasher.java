package io.github.heybadrinath.reconriver.workspace;

import io.github.heybadrinath.reconriver.shared.config.ReconRiverProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class TokenHasher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] secret;

    TokenHasher(ReconRiverProperties properties) {
        this.secret = properties.security().tokenHashSecret().getBytes(StandardCharsets.UTF_8);
    }

    String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Token hashing is unavailable", exception);
        }
    }
}
