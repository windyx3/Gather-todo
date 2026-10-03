package com.windy.todo;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface TaskRepository extends JpaRepository<TodoTask, Long>, JpaSpecificationExecutor<TodoTask> {
    Optional<TodoTask> findByIdAndProjectOwnerId(Long id, Long ownerId);
    @Modifying(flushAutomatically = true)
    @Query("delete from TodoTask t where t.project.id = :projectId")
    void deleteForProject(@Param("projectId") long projectId);
    @Modifying(flushAutomatically = true)
    @Query("delete from TodoTask t where t.project.id in (select p.id from Project p where p.owner.id = :userId)")
    void deleteForUser(@Param("userId") long userId);
}
