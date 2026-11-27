package dev.mosiur.drill.drill.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import dev.mosiur.drill.drill.domain.Drill;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DrillRepository extends JpaRepository<Drill, Long> {

    Optional<Drill> findByScheduledFor(LocalDate scheduledFor);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Drill d where d.id = :id")
    Optional<Drill> findByIdForUpdate(@Param("id") long id);

    @Query("""
            select d.id from Drill d
            where d.status = dev.mosiur.drill.drill.domain.DrillStatus.GRADING and d.submittedAt < :before
              and not exists (select 1 from Answer a, Grade g where a.drillId = d.id and g.answerId = a.id)
            """)
    List<Long> findUngradedSubmittedBefore(@Param("before") Instant before);

    @Query("""
            select d.id from Drill d, Answer a
            where a.drillId = d.id and a.round = 2 and a.updatedAt < :before
              and d.status = dev.mosiur.drill.drill.domain.DrillStatus.FOLLOW_UP
              and not exists (select 1 from Grade g where g.answerId = a.id)
            """)
    List<Long> findUngradedFollowUpsBefore(@Param("before") Instant before);

    @Modifying
    @Query(value = """
            insert into drill (problem_id, scheduled_for, kind, status)
            values (:problemId, :day, :kind, 'ASSIGNED')
            on conflict (scheduled_for) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("problemId") long problemId, @Param("day") LocalDate day, @Param("kind") String kind);
}
