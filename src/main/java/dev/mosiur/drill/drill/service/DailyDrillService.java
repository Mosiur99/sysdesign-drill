package dev.mosiur.drill.drill.service;

import java.util.Optional;

import dev.mosiur.drill.drill.model.TodayDrill;

public interface DailyDrillService {

    Optional<TodayDrill> assignToday();
}
