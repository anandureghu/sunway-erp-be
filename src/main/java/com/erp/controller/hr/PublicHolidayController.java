package com.erp.controller.hr;

import com.erp.dto.hr.PublicHolidayDTO;
import com.erp.service.hr.PublicHolidayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * HR Settings → Policies → Public Holidays. Any signed-in member of the company can
 * read the calendar (timesheets / leave show it); changes need HR settings or HR
 * Policies edit access — enforced in {@link PublicHolidayService}.
 */
@RestController
@RequestMapping("/api/hr/public-holidays")
@RequiredArgsConstructor
public class PublicHolidayController {

    private final PublicHolidayService service;

    @GetMapping
    public ResponseEntity<List<PublicHolidayDTO>> list(@RequestParam(name = "year", required = false) Integer year) {
        return ResponseEntity.ok(service.list(year != null ? year : LocalDate.now().getYear()));
    }

    @PostMapping
    public ResponseEntity<PublicHolidayDTO> create(@RequestBody PublicHolidayDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PublicHolidayDTO> update(@PathVariable("id") Long id, @RequestBody PublicHolidayDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Add Qatar's official holidays for the year (Eid dates estimated, flagged unconfirmed). */
    @PostMapping("/load-qatar")
    public ResponseEntity<List<PublicHolidayDTO>> loadQatar(@RequestParam("year") int year) {
        return ResponseEntity.ok(service.loadQatarHolidays(year));
    }

    /** Copy one year's holidays to another (moving holidays re-estimated, flagged for review). */
    @PostMapping("/copy")
    public ResponseEntity<List<PublicHolidayDTO>> copy(
            @RequestParam("fromYear") int fromYear,
            @RequestParam("toYear") int toYear) {
        return ResponseEntity.ok(service.copyYear(fromYear, toYear));
    }
}
