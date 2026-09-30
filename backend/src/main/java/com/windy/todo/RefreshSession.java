package com.windy.todo;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "refresh_sessions")
public class RefreshSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") AppUser user;
    @Column(nullable = false, unique = true, length = 64) String tokenHash;
    @Column(nullable = false, length = 36) String familyId;
    @Column(nullable = false) Instant expiresAt;
    @Column(nullable = false) boolean revoked;
    protected RefreshSession() {}
    RefreshSession(AppUser user, String hash, String familyId, Instant expiresAt) {
        this.user = user; this.tokenHash = hash; this.familyId = familyId; this.expiresAt = expiresAt;
    }
}
