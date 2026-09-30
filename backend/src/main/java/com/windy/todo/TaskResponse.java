package com.windy.todo;
import java.time.*;
public record TaskResponse(Long id, Long projectId, String title, String description,
        TodoTask.Priority priority, LocalDate dueDate, boolean completed, Instant createdAt, Instant updatedAt) {
    static TaskResponse of(TodoTask t) {
        return new TaskResponse(t.id, t.project.getId(), t.title, t.description, t.priority, t.dueDate,
                t.completed, t.createdAt, t.updatedAt);
    }
}
