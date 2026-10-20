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

        public List<LocalTime> getComputedStartTimes() {
        List<LocalTime> times = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            times.add(getStartTimeOf(i));
        }
        return times;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public List<Block> getBlocks() { return blocks; }
    public void setBlocks(List<Block> blocks) { this.blocks = blocks; }

}