package dev.mosiur.drill.grading.service;

import java.util.List;
import java.util.Map;

public interface FollowUpService {

    void submit(long drillId, Map<String, String> fields);

    void skip(long drillId);

    void grade(long drillId);

    void regrade(long drillId);

    List<Long> stuckDrills();
}
