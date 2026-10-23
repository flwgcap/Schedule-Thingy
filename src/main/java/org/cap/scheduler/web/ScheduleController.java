package org.cap.scheduler.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.cap.scheduler.model.Schedule;
import org.cap.scheduler.repository.ScheduleRepository;
import org.cap.scheduler.service.ExcelService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleRepository repository;
    private final ExcelService excelService;

    public ScheduleController(ScheduleRepository repository, ExcelService excelService) {
        this.repository = repository;
        this.excelService = excelService;
    }

    @GetMapping
    public List<Schedule> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Schedule> get(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Schedule create(@RequestBody Schedule schedule) {
        return repository.save(schedule);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Schedule> update(@PathVariable Long id, @RequestBody Schedule schedule) {
        return repository.findById(id)
                .map(existing -> {
                    schedule.setId(id);
                    schedule.setOwnerId(existing.getOwnerId()); // keep the owner, ready for accounts later
                    return ResponseEntity.ok(repository.save(schedule));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Builds an Excel file from the schedule the page sends (so unsaved edits are included)
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody Schedule schedule) throws IOException {
        byte[] bytes = excelService.export(schedule);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    // Reads an uploaded Excel file and saves it as a new schedule
    @PostMapping("/import")
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            Schedule imported = excelService.importSchedule(in, "Imported schedule");
            return ResponseEntity.ok(repository.save(imported));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Could not read that file. Use an .xlsx file exported from this app.");
        }
    }
}