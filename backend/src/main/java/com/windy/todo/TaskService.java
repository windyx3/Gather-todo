package com.windy.todo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;
import static com.windy.todo.ApiModels.*;

@Service @Transactional
public class TaskService {
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final UserRepository users;
    public TaskService(ProjectRepository projects, TaskRepository tasks, UserRepository users) {
        this.projects = projects; this.tasks = tasks; this.users = users;
    }
    @Transactional(readOnly=true)
    public List<ProjectResponse> projects(long userId) {
        return projects.findAllByOwnerIdOrderByCreatedAtAscIdAsc(userId).stream().map(ProjectResponse::of).toList();
    }
    public ProjectResponse createProject(long userId, ProjectInput input) {
        return ProjectResponse.of(projects.save(new Project(users.getReferenceById(userId), input.name().trim())));
    }
    public ProjectResponse renameProject(long userId, long id, ProjectInput input) {
        var p = project(userId, id); p.name = input.name().trim(); return ProjectResponse.of(p);
    }
    public void deleteProject(long userId, long id) {
        var p = project(userId, id);
        tasks.deleteForProject(id);
        projects.delete(p);
    }
    private Project project(long userId, long id) { return projects.findByIdAndOwnerId(id, userId).orElseThrow(ApiErrors::notFound); }
    @Transactional(readOnly=true)
    public TaskPage list(long userId, Long projectId, String query, Boolean completed,
                         TodoTask.Priority priority, int page, int size, String sort, String direction) {
        if (page < 0 || size < 1 || size > 100) throw ApiErrors.badRequest("page must be >= 0; size must be between 1 and 100");
        if (!Set.of("createdAt", "updatedAt", "dueDate", "title").contains(sort)) throw ApiErrors.badRequest("Unsupported sort field");
        if (!Set.of("asc", "desc").contains(direction)) throw ApiErrors.badRequest("direction must be asc or desc");
        if (query.length() > 200) throw ApiErrors.badRequest("Search is limited to 200 characters");
        if (projectId != null) project(userId, projectId);
        Specification<TodoTask> spec = (root, q, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("project").get("owner").get("id"), userId));
            if (projectId != null) predicates.add(cb.equal(root.get("project").get("id"), projectId));
            predicates.add(cb.equal(root.get("completed"), Boolean.TRUE.equals(completed)));
            if (priority != null) predicates.add(cb.equal(root.get("priority"), priority));
            if (!query.isBlank()) {
                String term = "%" + query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), term, '\\'), cb.like(cb.lower(root.get("description")), term, '\\')));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = tasks.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(direction), sort).and(Sort.by("id"))));
        return new TaskPage(result.getContent().stream().map(TaskResponse::of).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly=true)
    public TaskResponse get(long userId, long id) { return TaskResponse.of(task(userId, id)); }
    public TaskResponse create(long userId, long projectId, TaskInput input) {
        var task = new TodoTask(project(userId, projectId)); task.update(input); return TaskResponse.of(tasks.save(task));
    }
    public TaskResponse update(long userId, long id, TaskInput input) {
        var task = task(userId, id); task.update(input); return TaskResponse.of(task);
    }
    public void delete(long userId, long id) { tasks.delete(task(userId, id)); }
    private TodoTask task(long userId, long id) { return tasks.findByIdAndProjectOwnerId(id, userId).orElseThrow(ApiErrors::notFound); }
}
