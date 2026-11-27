package dev.mosiur.drill.grading.repository;

import java.util.Optional;

import dev.mosiur.drill.grading.domain.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    Optional<Grade> findByAnswerId(long answerId);

    boolean existsByAnswerId(long answerId);
}
