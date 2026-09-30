package com.windy.todo;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface RefreshRepository extends JpaRepository<RefreshSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshSession r where r.tokenHash = :hash")
    Optional<RefreshSession> lockByHash(@Param("hash") String hash);
    @Modifying(flushAutomatically = true)
    @Query("update RefreshSession r set r.revoked = true where r.familyId = :family")
    void revokeFamily(@Param("family") String family);
}
