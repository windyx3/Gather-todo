package com.windy.todo;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findAllByOwnerIdOrderByCreatedAtAscIdAsc(Long ownerId);
    Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);
    @Modifying(flushAutomatically = true)
    @Query("delete from Project p where p.owner.id = :userId")
    void deleteForUser(@Param("userId") long userId);
}
