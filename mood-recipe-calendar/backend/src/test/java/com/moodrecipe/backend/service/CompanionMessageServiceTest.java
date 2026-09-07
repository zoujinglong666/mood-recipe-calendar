package com.moodrecipe.backend.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanionMessageServiceTest {
    @Test void mapsLocalHourToMealPeriod() {
        assertEquals("早晨", CompanionMessageService.period(8));
        assertEquals("午间", CompanionMessageService.period(12));
        assertEquals("下午", CompanionMessageService.period(16));
        assertEquals("晚间", CompanionMessageService.period(20));
        assertEquals("深夜", CompanionMessageService.period(23));
    }
}
