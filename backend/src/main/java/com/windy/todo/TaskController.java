package com.windy.todo;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import static com.windy.todo.ApiModels.*;

@RestController @RequestMapping("/api")
public class TaskController {
    private final TaskService service;
    public TaskController(TaskService service) { this.service = service; }
    private long user(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
    @GetMapping("/projects") List<ProjectResponse> projects(@AuthenticationPrincipal Jwt jwt) { return service.projects(user(jwt)); }
    @PostMapping("/projects") ResponseEntity<ProjectResponse> createProject(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProjectInput input) {
        var result = service.createProject(user(jwt), input); return ResponseEntity.created(URI.create("/api/projects/" + result.id())).body(result);
    }
    @PutMapping("/projects/{id}") ProjectResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody ProjectInput input) {
        return service.renameProject(user(jwt), id, input);
    }
    @DeleteMapping("/projects/{id}") ResponseEntity<Void> deleteProject(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.deleteProject(user(jwt), id); return ResponseEntity.noContent().build();
    }
    @GetMapping({"/tasks", "/projects/{projectId}/tasks"})
    TaskPage list(@AuthenticationPrincipal Jwt jwt, @PathVariable(required=false) Long projectId,
            @RequestParam(defaultValue="") String q, @RequestParam(required=false) Boolean completed,
            @RequestParam(required=false) TodoTask.Priority priority, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size, @RequestParam(defaultValue="createdAt") String sort,
            @RequestParam(defaultValue="desc") String direction) {
        return service.list(user(jwt), projectId, q, completed, priority, page, size, sort, direction);
    }
    @GetMapping("/tasks/{id}") TaskResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { return service.get(user(jwt), id); }
    @PostMapping("/projects/{projectId}/tasks")
    ResponseEntity<TaskResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable long projectId, @Valid @RequestBody TaskInput input) {
        var result = service.create(user(jwt), projectId, input); return ResponseEntity.created(URI.create("/api/tasks/" + result.id())).body(result);
    }
    @PutMapping("/tasks/{id}") TaskResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody TaskInput input) {
        return service.update(user(jwt), id, input);
    }
    @DeleteMapping("/tasks/{id}") ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        service.delete(user(jwt), id); return ResponseEntity.noContent().build();
    }
}
