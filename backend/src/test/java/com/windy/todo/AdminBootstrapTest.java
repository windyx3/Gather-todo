package com.windy.todo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    @Test void restartDoesNotOverwriteAnExistingAdminPassword() {
        var users = mock(UserRepository.class); var encoder = mock(PasswordEncoder.class);
        var admin = new AppUser("admin@qq.com", "existing-hash", "My admin"); admin.role = AppUser.Role.ADMIN;
        when(users.findByEmail("admin@qq.com")).thenReturn(Optional.of(admin));
        new AdminBootstrap(users, encoder, "admin@qq.com", "admin").run(new DefaultApplicationArguments());
        assertThat(admin.passwordHash).isEqualTo("existing-hash"); assertThat(admin.displayName).isEqualTo("My admin");
        verifyNoInteractions(encoder); verify(users, never()).saveAndFlush(any());
    }
    @Test void existingOrdinaryAccountIsNotSilentlyPromoted() {
        var users = mock(UserRepository.class); var encoder = mock(PasswordEncoder.class);
        var user = new AppUser("admin@qq.com", "existing-hash", "Regular user");
        when(users.findByEmail("admin@qq.com")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> new AdminBootstrap(users, encoder, "admin@qq.com", "admin").run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(user.role).isEqualTo(AppUser.Role.USER); verifyNoInteractions(encoder);
    }
}
