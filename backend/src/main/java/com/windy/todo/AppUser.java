package com.windy.todo;
import jakarta.persistence.*;

@Entity @Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, unique = true, length = 254) String email;
    @Column(name = "password_hash", nullable = false, length = 100) String passwordHash;
    @Column(name = "display_name", nullable = false, length = 80) String displayName;
    protected AppUser() {}
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    AppUser(String email, String passwordHash, String displayName) {
        this.email = email; this.passwordHash = passwordHash; this.displayName = displayName;
    }
}
