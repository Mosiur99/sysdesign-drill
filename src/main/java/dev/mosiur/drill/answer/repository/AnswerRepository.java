package dev.mosiur.drill.answer.repository;

import java.util.Optional;

import dev.mosiur.drill.answer.domain.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    Optional<Answer> findByDrillIdAndRound(long drillId, short round);
}
