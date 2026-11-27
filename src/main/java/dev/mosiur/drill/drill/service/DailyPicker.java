package dev.mosiur.drill.drill.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import dev.mosiur.drill.drill.model.Candidate;
import dev.mosiur.drill.drill.model.Pick;

public interface DailyPicker {

    Optional<Pick> pick(List<Candidate> candidates, LocalDate day);
}
