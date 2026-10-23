package org.cap.scheduler.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.cap.scheduler.model.Block;
import org.cap.scheduler.model.Day;
import org.cap.scheduler.model.Schedule;
import org.springframework.stereotype.Service;

@Service
public class ExcelService {

    private static final String[] HEADERS = {"Time", "Activity", "Minutes", "Location", "POC", "Notes"};
    private static final int[] WIDTHS = {16, 32, 10, 24, 20, 30};
    private static final int HEADER_ROW = 3;      // zero-based, so row 4 in Excel
    private static final int MAX_DAYS = 31;
    private static final int MAX_BLOCKS = 500;
    private static final int MAX_TEXT = 255;      // matches the database column size
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})");

    // ---------------------------------------------------------------- export

    public byte[] export(Schedule schedule) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            CellStyle boldStyle = wb.createCellStyle();
            boldStyle.setFont(boldFont);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle timeStyle = wb.createCellStyle();
            timeStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("hh:mm"));
            timeStyle.setAlignment(HorizontalAlignment.LEFT);

            Set<String> usedNames = new HashSet<>();
            List<Day> days = schedule.getDays();
            for (int d = 0; d < days.size(); d++) {
                Day day = days.get(d);
                Sheet sheet = wb.createSheet(sheetName(day.getName(), d + 1, usedNames));
                writeDay(sheet, schedule.getName(), day, boldStyle, headerStyle, timeStyle);
            }
            if (days.isEmpty()) {
                wb.createSheet("Day 1");
            }

            // Work out the formula results now so every program can show them
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writeDay(Sheet sheet, String scheduleName, Day day,
                          CellStyle boldStyle, CellStyle headerStyle, CellStyle timeStyle) {
        Row r0 = sheet.createRow(0);
        Cell scheduleLabel = r0.createCell(0);
        scheduleLabel.setCellValue("Schedule");
        scheduleLabel.setCellStyle(boldStyle);
        r0.createCell(1).setCellValue(scheduleName == null ? "" : scheduleName);

        Row r1 = sheet.createRow(1);
        Cell startLabel = r1.createCell(0);
        startLabel.setCellValue("Day start time");
        startLabel.setCellStyle(boldStyle);
        LocalTime start = day.getStartTime() == null ? LocalTime.of(6, 0) : day.getStartTime();
        Cell startCell = r1.createCell(1);
        startCell.setCellValue(start.toSecondOfDay() / 86400.0); // Excel stores times as a fraction of a day
        startCell.setCellStyle(timeStyle);

        Row head = sheet.createRow(HEADER_ROW);
        for (int c = 0; c < HEADERS.length; c++) {
            Cell cell = head.createCell(c);
            cell.setCellValue(HEADERS[c]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(c, WIDTHS[c] * 256);
        }

        List<Block> blocks = day.getBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            int rowIndex = HEADER_ROW + 1 + i;
            Row row = sheet.createRow(rowIndex);

            // Excel rows start at 1, so "rowIndex" is the Excel row number of the row above this one
            Cell time = row.createCell(0);
            time.setCellFormula(i == 0 ? "$B$2" : "A" + rowIndex + "+C" + rowIndex + "/1440");
            time.setCellStyle(timeStyle);

            row.createCell(1).setCellValue(nullToEmpty(b.getTitle()));
            row.createCell(2).setCellValue(b.getDurationMinutes());
            row.createCell(3).setCellValue(nullToEmpty(b.getLocation()));
            row.createCell(4).setCellValue(nullToEmpty(b.getPoc()));
            row.createCell(5).setCellValue(nullToEmpty(b.getNotes()));
        }

        sheet.createFreezePane(0, HEADER_ROW + 1);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setPaperSize(PrintSetup.LETTER_PAPERSIZE);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
    }

    // Excel sheet names: max 31 characters, no \ / ? * [ ] : and each one must be unique
    private String sheetName(String raw, int number, Set<String> used) {
        String base = (raw == null || raw.isBlank())
                ? "Day " + number
                : WorkbookUtil.createSafeSheetName(raw.trim());
        if (base.length() > 31) {
            base = base.substring(0, 31);
        }
        String candidate = base;
        int n = 2;
        while (!used.add(candidate.toLowerCase())) {
            String suffix = " (" + n++ + ")";
            candidate = base.substring(0, Math.min(base.length(), 31 - suffix.length())) + suffix;
        }
        return candidate;
    }

    // ---------------------------------------------------------------- import

    public Schedule importSchedule(InputStream in, String fallbackName) throws IOException {
        Schedule schedule = new Schedule();
        schedule.setName(fallbackName);
        DataFormatter fmt = new DataFormatter();
        boolean nameFound = false;

        try (Workbook wb = WorkbookFactory.create(in)) {
            for (int s = 0; s < wb.getNumberOfSheets() && schedule.getDays().size() < MAX_DAYS; s++) {
                Sheet sheet = wb.getSheetAt(s);
                Day day = readDay(sheet, fmt);
                if (day == null) {
                    continue; // not one of our day sheets
                }
                schedule.getDays().add(day);
                if (!nameFound) {
                    String name = text(cell(sheet, 0, 1), fmt);
                    if (!name.isEmpty()) {
                        schedule.setName(name);
                        nameFound = true;
                    }
                }
            }
        }

        if (schedule.getDays().isEmpty()) {
            throw new IllegalArgumentException("No schedule found in that file.");
        }
        return schedule;
    }

    private Day readDay(Sheet sheet, DataFormatter fmt) {
        // Find the header row by looking for "Activity" in the second column
        int headerRow = -1;
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 15); r++) {
            if ("activity".equalsIgnoreCase(text(cell(sheet, r, 1), fmt))) {
                headerRow = r;
                break;
            }
        }
        if (headerRow < 0) {
            return null;
        }

        LocalTime start = LocalTime.of(6, 0);
        for (int r = 0; r < headerRow; r++) {
            if (text(cell(sheet, r, 0), fmt).toLowerCase().startsWith("day start")) {
                LocalTime t = readTime(cell(sheet, r, 1), fmt);
                if (t != null) {
                    start = t;
                }
            }
        }

        Day day = new Day();
        day.setName(limit(sheet.getSheetName()));
        day.setStartTime(start);

        int last = Math.min(sheet.getLastRowNum(), headerRow + MAX_BLOCKS);
        for (int r = headerRow + 1; r <= last; r++) {
            String title = text(cell(sheet, r, 1), fmt);
            int minutes = readMinutes(cell(sheet, r, 2), fmt);
            String location = text(cell(sheet, r, 3), fmt);
            String poc = text(cell(sheet, r, 4), fmt);
            String notes = text(cell(sheet, r, 5), fmt);

            if (title.isEmpty() && minutes == 0 && location.isEmpty() && poc.isEmpty() && notes.isEmpty()) {
                continue; // blank row
            }
            Block b = new Block();
            b.setTitle(title);
            b.setDurationMinutes(minutes);
            b.setLocation(location);
            b.setPoc(poc);
            b.setNotes(notes);
            day.getBlocks().add(b);
        }
        return day;
    }

    // ---------------------------------------------------------------- helpers

    private Cell cell(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? null : row.getCell(colIndex);
    }

    private String text(Cell cell, DataFormatter fmt) {
        return cell == null ? "" : limit(fmt.formatCellValue(cell).trim());
    }

    private String limit(String s) {
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private int readMinutes(Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return 0;
        }
        long value;
        if (cell.getCellType() == CellType.NUMERIC) {
            value = Math.round(cell.getNumericCellValue());
        } else {
            try {
                value = Math.round(Double.parseDouble(fmt.formatCellValue(cell).trim()));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return (int) Math.max(0, Math.min(value, 1440));
    }

    private LocalTime readTime(Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            long minutes = Math.round((v - Math.floor(v)) * 1440) % 1440;
            return LocalTime.of((int) (minutes / 60), (int) (minutes % 60));
        }
        String text = fmt.formatCellValue(cell).trim().toUpperCase();
        Matcher m = CLOCK.matcher(text);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            if (text.contains("PM") && h < 12) {
                h += 12;
            } else if (text.contains("AM") && h == 12) {
                h = 0;
            }
            if (h < 24 && min < 60) {
                return LocalTime.of(h, min);
            }
        }
        return null;
    }
}