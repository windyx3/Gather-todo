package com.windy.todo;
import jakarta.persistence.*;

@Entity @Table(name = "app_users")
public class AppUser {
    public enum Role { USER, ADMIN }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, unique = true, length = 254) String email;
    @Column(name = "password_hash", nullable = false, length = 100) String passwordHash;
    @Column(name = "display_name", nullable = false, length = 80) String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) Role role = Role.USER;
    @Column(name = "token_version", nullable = false) int tokenVersion;
    protected AppUser() {}
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    AppUser(String email, String passwordHash, String displayName) {
        this.email = email; this.passwordHash = passwordHash; this.displayName = displayName;
    }
}
