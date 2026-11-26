package dev.mosiur.drill.problem.repository;

import java.util.List;

import dev.mosiur.drill.problem.domain.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    List<Problem> findByActiveTrue();
}
