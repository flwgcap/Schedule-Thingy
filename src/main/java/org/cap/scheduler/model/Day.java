package org.cap.scheduler.model;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "schedule_day")
public class Day {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private LocalTime startTime;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "block_order")
    @JoinColumn(name = "day_id")
    private List<Block> blocks = new ArrayList<>();

    public LocalTime getStartTimeOf(int index) {
        LocalTime time = startTime;
        for (int i = 0; i < index; i++) {
            time = time.plusMinutes(blocks.get(i).getDurationMinutes());
        }
        return time;
    }

}