package com.windy.todo;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import static com.windy.todo.ApiModels.*;

@Service @Transactional
public class AdminService {
    private final UserRepository users;
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final RefreshRepository sessions;
    private final PasswordEncoder passwords;
    public AdminService(UserRepository users, TaskRepository tasks, ProjectRepository projects,
            RefreshRepository sessions, PasswordEncoder passwords) {
        this.users = users; this.tasks = tasks; this.projects = projects; this.sessions = sessions; this.passwords = passwords;
    }
    private void requireAdmin(long actorId) {
        var actor = users.findById(actorId).orElseThrow(ApiErrors::unauthorized);
        if (actor.role != AppUser.Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required");
    }
    @Transactional(readOnly=true)
    public UserPage list(long actorId, String query, int page, int size) {
        requireAdmin(actorId);
        if (page < 0 || size < 1 || size > 100 || query.length() > 200) throw ApiErrors.badRequest("Invalid user search or pagination");
        String term = "%" + query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<AppUser> spec = (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), term, '\\'), cb.like(cb.lower(root.get("displayName")), term, '\\'));
        var result = users.findAll(spec, PageRequest.of(page, size, Sort.by("email").and(Sort.by("id"))));
        return new UserPage(result.getContent().stream().map(UserResponse::of).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }
    public UserResponse update(long actorId, long id, AdminUserInput input) {
        requireAdmin(actorId);
        var target = users.findById(id).orElseThrow(ApiErrors::notFound);
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (target.role == AppUser.Role.ADMIN && !email.equals(target.email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The administrator email cannot be changed");
        if (input.password() != null) {
            if (input.password().isBlank()) throw ApiErrors.badRequest("Password cannot be blank");
            AuthService.validatePasswordBytes(input.password());
            target.passwordHash = passwords.encode(input.password());
            target.tokenVersion++;
            sessions.deleteForUser(id);
        }
        target.email = email; target.displayName = input.displayName().trim();
        users.flush();
        return UserResponse.of(target);
    }
    public void delete(long actorId, long id) {
        requireAdmin(actorId);
        var target = users.findById(id).orElseThrow(ApiErrors::notFound);
        if (target.role == AppUser.Role.ADMIN || id == actorId)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Administrator accounts cannot be deleted");
        tasks.deleteForUser(id);
        projects.deleteForUser(id);
        sessions.deleteForUser(id);
        users.delete(target);
        users.flush();
    }
}
