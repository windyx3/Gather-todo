package com.windy.todo;
import jakarta.persistence.*;
import java.time.*;

@Entity @Table(name = "tasks")
public class TodoTask {
    public enum Priority { LOW, MEDIUM, HIGH }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id") Project project;
    @Column(nullable = false, length = 200) String title;
    @Column(nullable = false, length = 4000) String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) Priority priority;
    LocalDate dueDate;
    @Column(nullable = false) boolean completed;
    @Column(nullable = false) Instant createdAt;
    @Column(nullable = false) Instant updatedAt;
    protected TodoTask() {}
    TodoTask(Project project) { this.project = project; this.createdAt = Instant.now(); }
    void update(ApiModels.TaskInput input) {
        title = input.title().trim(); description = input.description() == null ? "" : input.description().trim();
        priority = input.priority(); dueDate = input.dueDate(); completed = input.completed(); updatedAt = Instant.now();
    }
}
