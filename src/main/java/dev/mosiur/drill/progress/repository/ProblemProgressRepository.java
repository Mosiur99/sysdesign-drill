package dev.mosiur.drill.progress.repository;

import java.util.List;

import dev.mosiur.drill.progress.domain.ProblemProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProblemProgressRepository extends JpaRepository<ProblemProgress, Long> {

    @Query("select p from ProblemProgress p where p.attempts > 0")
    List<ProblemProgress> findAttempted();
}
