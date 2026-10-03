package com.windy.todo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String email, password;
    public AdminBootstrap(UserRepository users, PasswordEncoder passwords,
            @Value("${app.admin.email}") String email, @Value("${app.admin.password}") String password) {
        this.users = users; this.passwords = passwords;
        this.email = email.trim().toLowerCase(Locale.ROOT); this.password = password;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        var existing = users.findByEmail(email).orElse(null);
        if (existing != null) {
            if (existing.role != AppUser.Role.ADMIN)
                throw new IllegalStateException("Administrator email belongs to a regular account; configure a different ADMIN_EMAIL");
            return;
        }
        if (email.isBlank() || email.length() > 254 || !email.contains("@") || password.isBlank())
            throw new IllegalArgumentException("Set a valid ADMIN_EMAIL and non-empty ADMIN_PASSWORD");
        AuthService.validatePasswordBytes(password);
        var admin = new AppUser(email, passwords.encode(password), "Administrator");
        admin.role = AppUser.Role.ADMIN;
        users.saveAndFlush(admin);
    }
}
