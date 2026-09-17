package io.github.johneliud.identity_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm";
    private static final long ACCESS_TOKEN_EXPIRATION = 900000L;
    private static final long REFRESH_TOKEN_EXPIRATION = 604800000L;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("Generate access token returns non-null compact JWT")
    void generateAccessToken_returnsNonNullToken() {
        String token = jwtTokenProvider.generateAccessToken("user-123", Set.of("TRAVELER"),
                "test@example.com", "John", "Doe");
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Parse access token extracts correct claims")
    void parseAccessToken_extractsCorrectClaims() {
        String userId = "user-456";
        Set<String> roles = Set.of("ADMIN", "TRAVEL_MANAGER");

        String token = jwtTokenProvider.generateAccessToken(userId, roles,
                "test@example.com", "John", "Doe");
        Claims claims = jwtTokenProvider.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo(userId);
        assertThat(jwtTokenProvider.getRolesFromToken(token)).isEqualTo(roles);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("Validate token returns true for valid token")
    void validateToken_validToken_returnsTrue() {
        String token = jwtTokenProvider.generateAccessToken("user-789", Set.of("TRAVELER"),
                "test@example.com", "John", "Doe");
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("Validate token returns false for invalid token")
    void validateToken_invalidToken_returnsFalse() {
        assertThat(jwtTokenProvider.validateToken("invalid.jwt.token")).isFalse();
    }

    @Test
    @DisplayName("Validate token returns false for tampered token")
    void validateToken_tamperedToken_returnsFalse() {
        String token = jwtTokenProvider.generateAccessToken("user-789", Set.of("TRAVELER"),
                "test@example.com", "John", "Doe");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtTokenProvider.validateToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("Get userId from token extracts subject")
    void getUserIdFromToken_extractsSubject() {
        String userId = "user-abc";
        String token = jwtTokenProvider.generateAccessToken(userId, Set.of("TRAVELER"),
                "test@example.com", "John", "Doe");
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("Get roles from token extracts role claim")
    void getRolesFromToken_extractsRoles() {
        Set<String> expectedRoles = Set.of("TRAVEL_MANAGER", "TRAVELER");
        String token = jwtTokenProvider.generateAccessToken("user-xyz", expectedRoles,
                "test@example.com", "John", "Doe");
        assertThat(jwtTokenProvider.getRolesFromToken(token)).isEqualTo(expectedRoles);
    }

    @Test
    @DisplayName("Generate refresh token returns unique tokens")
    void generateRefreshToken_returnsUniqueTokens() {
        String token1 = jwtTokenProvider.generateRefreshToken();
        String token2 = jwtTokenProvider.generateRefreshToken();
        assertThat(token1).isNotBlank();
        assertThat(token2).isNotBlank();
        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    @DisplayName("Access token expiration returns configured value")
    void getAccessTokenExpirationMs_returnsConfiguredValue() {
        assertThat(jwtTokenProvider.getAccessTokenExpirationMs()).isEqualTo(ACCESS_TOKEN_EXPIRATION);
    }

    @Test
    @DisplayName("Refresh token expiration returns configured value")
    void getRefreshTokenExpirationMs_returnsConfiguredValue() {
        assertThat(jwtTokenProvider.getRefreshTokenExpirationMs()).isEqualTo(REFRESH_TOKEN_EXPIRATION);
    }
}
