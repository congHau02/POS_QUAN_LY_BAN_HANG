package learn.java.billingsoftware;

import io.jsonwebtoken.JwtException;
import learn.java.billingsoftware.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "SECRET_KEY", "thisismysecretkeyfortheupcomingprojectthatislongenough");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpirationMs", 900000L);
    }

    @Test
    void testGenerateAndValidateToken() {
        UserDetails userDetails = new User("cashier@billing.com", "password", Collections.emptyList());
        String token = jwtUtil.generateToken(userDetails);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("cashier@billing.com");
        assertThat(jwtUtil.validateToken(token, userDetails)).isTrue();
    }

    @Test
    void testValidateTokenWithDifferentUserReturnsFalse() {
        UserDetails userDetails = new User("cashier@billing.com", "password", Collections.emptyList());
        UserDetails otherUser = new User("other@billing.com", "password", Collections.emptyList());

        String token = jwtUtil.generateToken(userDetails);
        assertThat(jwtUtil.validateToken(token, otherUser)).isFalse();
    }

    @Test
    void testExtractExpirationIsFuture() {
        UserDetails userDetails = new User("cashier@billing.com", "password", Collections.emptyList());
        String token = jwtUtil.generateToken(userDetails);

        Date expiration = jwtUtil.extractExpiration(token);
        assertThat(expiration).isAfter(new Date());
    }

    @Test
    void testInvalidTokenThrowsJwtException() {
        assertThatThrownBy(() -> jwtUtil.extractUsername("invalid.tampered.token"))
                .isInstanceOf(JwtException.class);
    }
}
