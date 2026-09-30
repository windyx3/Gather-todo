package com.windy.todo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import static com.windy.todo.ApiModels.*;

@Service @Transactional
public class AuthService {
    public record SessionResult(AuthResponse response, String refreshToken, Instant refreshExpiry) {}
    private final UserRepository users;
    private final RefreshRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String issuer, audience, dummyHash;
    private final SecureRandom random = new SecureRandom();
    public AuthService(UserRepository users, RefreshRepository sessions, PasswordEncoder passwords, JwtEncoder encoder,
            @Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.audience}") String audience) {
        this.users = users; this.sessions = sessions; this.passwords = passwords; this.encoder = encoder;
        this.issuer = issuer; this.audience = audience; this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }
    public SessionResult register(RegisterInput input) {
        validatePasswordBytes(input.password());
        var user = users.saveAndFlush(new AppUser(normalize(input.email()), passwords.encode(input.password()), input.displayName().trim()));
        return issue(user, UUID.randomUUID().toString(), Instant.now().plus(Duration.ofDays(7)));
    }
    public SessionResult login(LoginInput input) {
        validatePasswordBytes(input.password());
        var user = users.findByEmail(normalize(input.email())).orElse(null);
        boolean matches = passwords.matches(input.password(), user == null ? dummyHash : user.passwordHash);
        if (user == null || !matches) throw ApiErrors.unauthorized();
        return issue(user, UUID.randomUUID().toString(), Instant.now().plus(Duration.ofDays(7)));
    }
    // Reuse revocation must COMMIT even though the request returns 401.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public SessionResult refresh(String raw) {
        if (raw == null || raw.length() > 128) throw ApiErrors.unauthorized();
        var current = sessions.lockByHash(hash(raw)).orElseThrow(ApiErrors::unauthorized);
        if (current.revoked || !current.expiresAt.isAfter(Instant.now())) {
            sessions.revokeFamily(current.familyId);
            throw ApiErrors.unauthorized();
        }
        current.revoked = true;
        return issue(current.user, current.familyId, current.expiresAt);
    }
    public void logout(String raw) {
        if (raw != null && raw.length() <= 128) sessions.lockByHash(hash(raw)).ifPresent(s -> sessions.revokeFamily(s.familyId));
    }
    @Transactional(readOnly=true)
    public UserResponse profile(long id) { return UserResponse.of(users.findById(id).orElseThrow(ApiErrors::unauthorized)); }
    public UserResponse updateProfile(long id, ProfileInput input) {
        var user = users.findById(id).orElseThrow(ApiErrors::unauthorized);
        user.displayName = input.displayName().trim(); return UserResponse.of(user);
    }
    private SessionResult issue(AppUser user, String familyId, Instant expiry) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(new RefreshSession(user, hash(raw), familyId, expiry));
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(600)).id(UUID.randomUUID().toString()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new SessionResult(new AuthResponse(token, 600, UserResponse.of(user)), raw, expiry);
    }
    static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private static void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw ApiErrors.badRequest("Password must be at most 72 UTF-8 bytes");
    }
}
