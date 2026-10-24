package learn.java.billingsoftware.controller;

import learn.java.billingsoftware.io.AuthRequest;
import learn.java.billingsoftware.io.AuthResponse;
import learn.java.billingsoftware.io.UserResponse;
import learn.java.billingsoftware.service.ActivityLogService;
import learn.java.billingsoftware.service.RefreshTokenService;
import learn.java.billingsoftware.service.UserService;
import learn.java.billingsoftware.service.impl.AppUserDetailsService;
import learn.java.billingsoftware.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class AuthController {


    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService appUserDetailsService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final ActivityLogService activityLogService;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.refresh-token.expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${auth.refresh-token.cookie-secure:false}")
    private boolean refreshTokenCookieSecure;

    @Value("${auth.refresh-token.cookie-same-site:Lax}")
    private String refreshTokenCookieSameSite;

    @Value("${auth.refresh-token.cookie-path:/api/v1.0}")
    private String refreshTokenCookiePath;

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest request, HttpServletResponse response) {
        authenticate(request.getEmail(), request.getPassword());
        final UserDetails userDetails = appUserDetailsService.loadUserByUsername(request.getEmail());
        final String jwtToken = jwtUtil.generateToken(userDetails);
        final RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(userDetails.getUsername());
        UserResponse user = userService.findByEmail(request.getEmail());
        activityLogService.logActivity(request.getEmail(), "LOGIN", "USER", request.getEmail(), "User logged in successfully");
        addRefreshTokenCookie(response, refreshToken.getRawToken());
        return new AuthResponse(request.getEmail(), jwtToken, user.getRole(), user.getName());
    }

    @PostMapping("/auth/refresh")
    public AuthResponse refresh(
            @CookieValue(value = "refreshToken", required = false) String rawRefreshToken,
            HttpServletResponse response
    ) {
        try {
            RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken);
            UserDetails userDetails = appUserDetailsService.loadUserByUsername(rotated.getUserEmail());
            UserResponse user = userService.findByEmail(userDetails.getUsername());
            addRefreshTokenCookie(response, rotated.getRawToken());
            return new AuthResponse(userDetails.getUsername(), jwtUtil.generateToken(userDetails), user.getRole(), user.getName());
        } catch (ResponseStatusException exception) {
            clearRefreshTokenCookie(response);
            throw exception;
        }
    }

    @PostMapping("/auth/logout")
    public void logout(
            @CookieValue(value = "refreshToken", required = false) String rawRefreshToken,
            HttpServletResponse response
    ) {
        refreshTokenService.revoke(rawRefreshToken);
        clearRefreshTokenCookie(response);
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String rawRefreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", rawRefreshToken)
                .httpOnly(true)
                .secure(refreshTokenCookieSecure)
                .sameSite(refreshTokenCookieSameSite)
                .path(refreshTokenCookiePath)
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(refreshTokenCookieSecure)
                .sameSite(refreshTokenCookieSameSite)
                .path(refreshTokenCookiePath)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void authenticate(String email, String password) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (DisabledException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản đã bị vô hiệu hóa");
        } catch (BadCredentialsException | org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác");
        }
    }
}
