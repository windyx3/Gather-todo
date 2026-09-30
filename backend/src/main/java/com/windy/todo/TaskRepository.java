package com.windy.todo;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface TaskRepository extends JpaRepository<TodoTask, Long>, JpaSpecificationExecutor<TodoTask> {
    Optional<TodoTask> findByIdAndProjectOwnerId(Long id, Long ownerId);
    boolean existsByProjectId(Long projectId);
}
