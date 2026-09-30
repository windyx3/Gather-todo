package com.windy.todo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.Map;
import static com.windy.todo.ApiModels.*;

@RestController @RequestMapping("/api")
public class AuthController {
    private final AuthService service;
    private final boolean secure;
    public AuthController(AuthService service, @Value("${app.cookie-secure}") boolean secure) { this.service = service; this.secure = secure; }
    @GetMapping("/auth/csrf") Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }
    @PostMapping("/auth/register") ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterInput input) { return response(service.register(input), 201); }
    @PostMapping("/auth/login") ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginInput input) { return response(service.login(input), 200); }
    @PostMapping("/auth/refresh") ResponseEntity<AuthResponse> refresh(@CookieValue(name="refresh_token", required=false) String token) {
        return response(service.refresh(token), 200);
    }
    @PostMapping("/auth/logout") ResponseEntity<Void> logout(@CookieValue(name="refresh_token", required=false) String token) {
        service.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }
    @GetMapping("/me") UserResponse me(@AuthenticationPrincipal Jwt jwt) { return service.profile(Long.parseLong(jwt.getSubject())); }
    @PutMapping("/me") UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileInput input) { return service.updateProfile(Long.parseLong(jwt.getSubject()), input); }
    private ResponseEntity<AuthResponse> response(AuthService.SessionResult result, int status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), Duration.between(Instant.now(), result.refreshExpiry())).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(result.response());
    }
    private ResponseCookie cookie(String token, Duration maxAge) {
        return ResponseCookie.from("refresh_token", token).httpOnly(true).secure(secure).sameSite("Strict")
                .path("/api/auth").maxAge(maxAge).build();
    }
}
