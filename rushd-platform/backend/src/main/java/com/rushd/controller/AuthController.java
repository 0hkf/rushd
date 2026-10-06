package com.rushd.controller;

import com.rushd.dto.*;
import com.rushd.entity.User;
import com.rushd.service.*;
import com.rushd.exception.InvalidRefreshTokenException;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final JwtService jwt;
    private final RefreshTokenService refresh;
    private final AuthCookieService cookies;
    private final CsrfTokenRepository csrf;
    public AuthController(AuthService auth, JwtService jwt, RefreshTokenService refresh,
                          AuthCookieService cookies, CsrfTokenRepository csrf) {
        this.auth = auth; this.jwt = jwt; this.refresh = refresh; this.cookies = cookies; this.csrf = csrf;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                              HttpServletResponse response) {
        User user = auth.authenticate(body);
        // Re-login replaces this browser's refresh family rather than leaving it active.
        refresh.revoke(cookies.read(request, AuthCookieService.REFRESH));
        var issued = refresh.createSession(user);
        cookies.issue(response, jwt.generateAccessToken(user.getEmail()), issued.credential());
        csrf.saveToken(null, request, response);
        return new AuthResponse(UserResponse.from(user));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            var issued = refresh.rotate(cookies.read(request, AuthCookieService.REFRESH));
            cookies.issue(response, jwt.generateAccessToken(issued.user().getEmail()), issued.credential());
            csrf.saveToken(null, request, response);
            return new AuthResponse(UserResponse.from(issued.user()));
        } catch (InvalidRefreshTokenException invalid) {
            cookies.clear(response);
            throw invalid;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        refresh.revoke(cookies.read(request, AuthCookieService.REFRESH));
        cookies.clear(response);
        csrf.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) { return auth.me(authentication.getName()); }
}
