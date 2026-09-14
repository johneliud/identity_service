package io.github.johneliud.identity_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenHashUtilTest {

    private final TokenHashUtil tokenHashUtil = new TokenHashUtil();

    @Test
    @DisplayName("hashToken returns consistent SHA-256 hash for same input")
    void hashToken_consistentHash() {
        String token = "my-raw-token";

        String hash1 = tokenHashUtil.hashToken(token);
        String hash2 = tokenHashUtil.hashToken(token);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("hashToken returns different hashes for different inputs")
    void hashToken_differentInputs() {
        String hash1 = tokenHashUtil.hashToken("token-a");
        String hash2 = tokenHashUtil.hashToken("token-b");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("hashToken returns Base64-encoded string")
    void hashToken_returnsBase64() {
        String hash = tokenHashUtil.hashToken("test-token");

        // Base64 characters: A-Z, a-z, 0-9, +, /, =
        assertThat(hash).matches("^[A-Za-z0-9+/]+=*$");
    }
}
