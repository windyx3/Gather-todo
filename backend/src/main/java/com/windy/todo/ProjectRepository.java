package com.windy.todo;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findAllByOwnerIdOrderByCreatedAtAscIdAsc(Long ownerId);
    Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);
}
