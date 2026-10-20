package org.cap.scheduler.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class DayTest {

    private Block block(String title, int minutes) {
        Block b = new Block();
        b.setTitle(title);
        b.setDurationMinutes(minutes);
        return b;
    }

    @Test
    void startTimesAreCalculatedFromDurations() {
        Day day = new Day();
        day.setStartTime(LocalTime.of(6, 0));
        day.getBlocks().add(block("Reveille", 30));
        day.getBlocks().add(block("Breakfast", 45));
        day.getBlocks().add(block("Drill", 60));

        assertEquals(LocalTime.of(6, 0), day.getStartTimeOf(0));
        assertEquals(LocalTime.of(6, 30), day.getStartTimeOf(1));
        assertEquals(LocalTime.of(7, 15), day.getStartTimeOf(2));
    }

    @Test
    void changingADurationShiftsLaterBlocks() {
        Day day = new Day();
        day.setStartTime(LocalTime.of(6, 0));
        day.getBlocks().add(block("Reveille", 30));
        day.getBlocks().add(block("Breakfast", 45));
        day.getBlocks().add(block("Drill", 60));

        // Make the first block 15 minutes longer
        day.getBlocks().get(0).setDurationMinutes(45);

        assertEquals(LocalTime.of(6, 45), day.getStartTimeOf(1));
        assertEquals(LocalTime.of(7, 30), day.getStartTimeOf(2));
    }
}