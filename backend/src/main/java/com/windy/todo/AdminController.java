package com.windy.todo;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.windy.todo.ApiModels.*;

@RestController @RequestMapping("/api/admin/users")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service = service; }
    @GetMapping
    UserPage list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        return service.list(Long.parseLong(jwt.getSubject()), q, page, size);
    }
    @PutMapping("/{id}")
    UserResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody AdminUserInput input) {
        return service.update(Long.parseLong(jwt.getSubject()), id, input);
    }
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.delete(Long.parseLong(jwt.getSubject()), id); return ResponseEntity.noContent().build();
    }
}
