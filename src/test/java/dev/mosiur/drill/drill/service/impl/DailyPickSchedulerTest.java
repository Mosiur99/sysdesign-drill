package dev.mosiur.drill.drill.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import dev.mosiur.drill.drill.service.DailyDrillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyPickSchedulerTest {

    @Mock
    private DailyDrillService dailyDrills;

    @Test
    void assignsTodaysDrillWhenTriggered() {
        when(dailyDrills.assignToday()).thenReturn(Optional.empty());

        new DailyPickScheduler(dailyDrills).assignToday();

        verify(dailyDrills).assignToday();
    }
}
