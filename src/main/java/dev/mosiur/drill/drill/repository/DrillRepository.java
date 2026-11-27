package dev.mosiur.drill.drill.repository;

import java.time.LocalDate;
import java.util.Optional;

import dev.mosiur.drill.drill.domain.Drill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DrillRepository extends JpaRepository<Drill, Long> {

    Optional<Drill> findByScheduledFor(LocalDate scheduledFor);

    @Modifying
    @Query(value = """
            insert into drill (problem_id, scheduled_for, kind, status)
            values (:problemId, :day, :kind, 'ASSIGNED')
            on conflict (scheduled_for) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("problemId") long problemId, @Param("day") LocalDate day, @Param("kind") String kind);
}
