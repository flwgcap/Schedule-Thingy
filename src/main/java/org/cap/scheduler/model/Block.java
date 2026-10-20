package org.cap.scheduler.model;

import jakarta.persistence.*;

@Entity
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private int durationMinutes;
    private String location;
    private String poc;
    private String notes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getPoc() { return poc; }
    public void setPoc(String poc) { this.poc = poc; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}