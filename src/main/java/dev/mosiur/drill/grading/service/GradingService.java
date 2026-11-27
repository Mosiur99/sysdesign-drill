package dev.mosiur.drill.grading.service;

import java.util.List;

import dev.mosiur.drill.grading.model.ResultView;

public interface GradingService {

    void grade(long drillId);

    void regrade(long drillId);

    List<Long> stuckDrills();

    ResultView result(long drillId);
}
