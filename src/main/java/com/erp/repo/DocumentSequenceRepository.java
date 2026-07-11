package com.erp.repo;

import com.erp.domain.DocumentSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, String> {

    /**
     * Row-locked lookup ({@code SELECT ... FOR UPDATE}) so two concurrent
     * generators for the same key serialise instead of colliding.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DocumentSequence s WHERE s.documentType = :key")
    Optional<DocumentSequence> findForUpdate(@Param("key") String key);
}
