package com.windy.todo;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class ApiModels {
    private ApiModels() {}
    public record RegisterInput(@NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(min=10, max=72) String password, @NotBlank @Size(max=80) String displayName) {}
    public record LoginInput(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
    public record ProfileInput(@NotBlank @Size(max=80) String displayName) {}
    public record UserResponse(Long id, String email, String displayName) {
        static UserResponse of(AppUser u) { return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName()); }
    }
    public record ProjectInput(@NotBlank @Size(max=100) String name) {}
    public record ProjectResponse(Long id, String name, Instant createdAt) {
        static ProjectResponse of(Project p) { return new ProjectResponse(p.id, p.name, p.createdAt); }
    }
    public record TaskInput(@NotBlank @Size(max=200) String title, @Size(max=4000) String description,
            @NotNull TodoTask.Priority priority, LocalDate dueDate, @NotNull Boolean completed) {}
    public record TaskPage(List<TaskResponse> content, int page, int size, long totalElements, int totalPages) {}
    public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {}
}
