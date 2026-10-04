package com.erp.service.hr;

import com.erp.domain.hr.PublicHoliday;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.hr.PublicHolidayDTO;
import com.erp.exception.ConflictException;
import com.erp.exception.NotFoundException;
import com.erp.repo.hr.PublicHolidayRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.LeaveAttendanceUtil;
import com.erp.service.security.PermissionCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * HR Settings → Policies → Public Holidays, and the single source every other HR
 * calculation asks "is this day a public holiday?".
 *
 * <p>A public holiday on a working day (Sun–Thu) is a paid day off: the employee is
 * not marked absent, the day is not deducted from leave, and payroll pays it. Work
 * done on it is holiday overtime. Holidays falling on the weekend change nothing.</p>
 *
 * <p>Islamic holidays move every year (and are confirmed only after the moon
 * sighting), so HR can load estimated dates for a year and adjust them later.</p>
 */
@Service
@RequiredArgsConstructor
public class PublicHolidayService {

    private static final int MAX_HOLIDAY_DAYS = 30;

    private final PublicHolidayRepository repo;
    private final AuthContext authContext;
    private final PermissionCheckService permissionCheck;

    // ------------------------------------------------------------------
    // Lookups used by attendance, leave and payroll
    // ------------------------------------------------------------------

    /** Every date covered by a holiday in [from, to] (weekend dates included), with its name. */
    @Transactional(readOnly = true)
    public Map<LocalDate, String> holidayNames(Long companyId, LocalDate from, LocalDate to) {
        Map<LocalDate, String> map = new TreeMap<>();
        if (companyId == null || from == null || to == null || to.isBefore(from)) {
            return map;
        }
        for (PublicHoliday h : overlapping(companyId, from, to)) {
            LocalDate d = h.getStartDate().isBefore(from) ? from : h.getStartDate();
            LocalDate end = h.getEndDate().isAfter(to) ? to : h.getEndDate();
            for (; !d.isAfter(end); d = d.plusDays(1)) {
                map.putIfAbsent(d, h.getName());
            }
        }
        return map;
    }

    /** Dates covered by a holiday in [from, to] (weekend dates included). */
    @Transactional(readOnly = true)
    public Set<LocalDate> holidayDates(Long companyId, LocalDate from, LocalDate to) {
        return companyId == null ? Collections.emptySet()
                : new HashSet<>(holidayNames(companyId, from, to).keySet());
    }

    /** The holiday's name when {@code date} is a public holiday for the company. */
    @Transactional(readOnly = true)
    public Optional<String> holidayOn(Long companyId, LocalDate date) {
        return Optional.ofNullable(holidayNames(companyId, date, date).get(date));
    }

    // ------------------------------------------------------------------
    // HR Settings CRUD
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PublicHolidayDTO> list(int year) {
        Long companyId = requireCompany();
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        return overlapping(companyId, from, to).stream().map(this::toDTO).toList();
    }

    @Transactional
    public PublicHolidayDTO create(PublicHolidayDTO dto) {
        Long companyId = requireEditor();
        PublicHoliday h = new PublicHoliday();
        h.setCompanyId(companyId);
        apply(h, dto);
        assertNoOverlap(companyId, h, null);
        return toDTO(repo.save(h));
    }

    @Transactional
    public PublicHolidayDTO update(Long id, PublicHolidayDTO dto) {
        Long companyId = requireEditor();
        PublicHoliday h = getOwned(id, companyId);
        apply(h, dto);
        assertNoOverlap(companyId, h, id);
        return toDTO(repo.save(h));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = requireEditor();
        repo.delete(getOwned(id, companyId));
    }

    /**
     * Adds Qatar's official public holidays for {@code year} that aren't there yet:
     * National Sports Day (2nd Tuesday of February), Eid al-Fitr and Eid al-Adha
     * (3 days each — Qatar Labour Law minimum — dates estimated from the Umm al-Qura
     * calendar and flagged unconfirmed) and National Day (18 December).
     */
    @Transactional
    public List<PublicHolidayDTO> loadQatarHolidays(int year) {
        Long companyId = requireEditor();
        validateYear(year);

        List<PublicHoliday> template = new ArrayList<>();
        LocalDate sportsDay = LocalDate.of(year, 2, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.TUESDAY));
        template.add(build(companyId, "National Sports Day", sportsDay, sportsDay,
                PublicHoliday.Category.NATIONAL, false, true,
                "Second Tuesday of February."));
        estimateHijri(year, 10, 1).ifPresent(d -> template.add(build(companyId, "Eid al-Fitr",
                d, d.plusDays(2), PublicHoliday.Category.RELIGIOUS, false, false,
                "Estimated from the Umm al-Qura calendar — confirm once officially announced.")));
        estimateHijri(year, 12, 10).ifPresent(d -> template.add(build(companyId, "Eid al-Adha",
                d, d.plusDays(2), PublicHoliday.Category.RELIGIOUS, false, false,
                "Estimated from the Umm al-Qura calendar — confirm once officially announced.")));
        LocalDate nationalDay = LocalDate.of(year, 12, 18);
        template.add(build(companyId, "Qatar National Day", nationalDay, nationalDay,
                PublicHoliday.Category.NATIONAL, true, true, "18 December every year."));

        List<PublicHoliday> existing = overlapping(companyId,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        for (PublicHoliday h : template) {
            boolean sameName = existing.stream().anyMatch(e -> sameName(e.getName(), h.getName()));
            boolean clashes = existing.stream().anyMatch(e -> overlaps(e, h));
            if (!sameName && !clashes) {
                repo.save(h);
            }
        }
        return list(year);
    }

    /**
     * Copies {@code fromYear}'s holidays into {@code toYear} (skipping ones already
     * there). Recurring holidays keep their date; the others (e.g. Eid) are copied as
     * unconfirmed so HR adjusts them — Islamic holidays move ~11 days each year, so
     * their dates are re-estimated from the Hijri calendar where possible.
     */
    @Transactional
    public List<PublicHolidayDTO> copyYear(int fromYear, int toYear) {
        Long companyId = requireEditor();
        validateYear(fromYear);
        validateYear(toYear);
        if (fromYear == toYear) {
            throw new IllegalArgumentException("Choose a different year to copy from.");
        }
        List<PublicHoliday> source = overlapping(companyId,
                LocalDate.of(fromYear, 1, 1), LocalDate.of(fromYear, 12, 31));
        List<PublicHoliday> existing = overlapping(companyId,
                LocalDate.of(toYear, 1, 1), LocalDate.of(toYear, 12, 31));
        int shift = toYear - fromYear;

        for (PublicHoliday src : source) {
            if (src.getStartDate().getYear() != fromYear) continue; // spans from the year before
            long length = ChronoUnit.DAYS.between(src.getStartDate(), src.getEndDate());
            LocalDate start = src.getStartDate().plusYears(shift);
            boolean confirmed = src.isRecurring();
            if (!src.isRecurring()) {
                Optional<LocalDate> reestimated = reestimate(src.getStartDate(), toYear);
                if (reestimated.isPresent()) start = reestimated.get();
            }
            PublicHoliday copy = build(companyId, src.getName(), start, start.plusDays(length),
                    src.getCategory(), src.isRecurring(), confirmed,
                    src.isRecurring() ? src.getNotes()
                            : "Copied from " + fromYear + " — review the dates.");
            boolean sameName = existing.stream().anyMatch(e -> sameName(e.getName(), copy.getName()));
            boolean clashes = existing.stream().anyMatch(e -> overlaps(e, copy));
            if (!sameName && !clashes) {
                existing.add(repo.save(copy));
            }
        }
        return list(toYear);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<PublicHoliday> overlapping(Long companyId, LocalDate from, LocalDate to) {
        return repo.findByCompanyIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(
                companyId, to, from);
    }

    private void apply(PublicHoliday h, PublicHolidayDTO dto) {
        if (dto == null) throw new IllegalArgumentException("Holiday details are required.");
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Enter the holiday name.");
        if (name.length() > 120) throw new IllegalArgumentException("Holiday name is too long (max 120).");
        if (dto.getStartDate() == null) throw new IllegalArgumentException("Select the start date.");
        LocalDate end = dto.getEndDate() != null ? dto.getEndDate() : dto.getStartDate();
        if (end.isBefore(dto.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before the start date.");
        }
        if (ChronoUnit.DAYS.between(dto.getStartDate(), end) + 1 > MAX_HOLIDAY_DAYS) {
            throw new IllegalArgumentException("A holiday can span at most " + MAX_HOLIDAY_DAYS + " days.");
        }
        PublicHoliday.Category category = PublicHoliday.Category.NATIONAL;
        if (dto.getCategory() != null && !dto.getCategory().isBlank()) {
            try {
                category = PublicHoliday.Category.valueOf(dto.getCategory().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown holiday category: " + dto.getCategory());
            }
        }
        h.setName(name);
        h.setStartDate(dto.getStartDate());
        h.setEndDate(end);
        h.setCategory(category);
        h.setRecurring(dto.isRecurring());
        h.setConfirmed(dto.isConfirmed());
        String notes = dto.getNotes() == null ? null : dto.getNotes().trim();
        h.setNotes(notes == null || notes.isEmpty() ? null : notes);
    }

    private void assertNoOverlap(Long companyId, PublicHoliday h, Long selfId) {
        for (PublicHoliday other : overlapping(companyId, h.getStartDate(), h.getEndDate())) {
            if (!Objects.equals(other.getId(), selfId)) {
                throw new ConflictException("These dates overlap \"" + other.getName() + "\" ("
                        + other.getStartDate() + (other.getEndDate().equals(other.getStartDate())
                        ? "" : " – " + other.getEndDate()) + "). Edit that holiday instead.");
            }
        }
    }

    private PublicHoliday getOwned(Long id, Long companyId) {
        PublicHoliday h = repo.findById(id).orElseThrow(() -> new NotFoundException("Holiday not found"));
        if (!Objects.equals(h.getCompanyId(), companyId)) throw new NotFoundException("Holiday not found");
        return h;
    }

    private Long requireCompany() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) throw new AccessDeniedException("No company context for the current user");
        return companyId;
    }

    /** Changing holidays needs edit access to HR settings or the HR Policies tab. */
    private Long requireEditor() {
        Long companyId = requireCompany();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = permissionCheck.hasAny(auth, AppModule.HR_SETTINGS, AppAction.EDIT)
                || permissionCheck.hasAny(auth, AppModule.HRS_POLICIES, AppAction.EDIT);
        if (!allowed) {
            throw new AccessDeniedException("HR settings edit permission is required to change public holidays");
        }
        return companyId;
    }

    private static void validateYear(int year) {
        if (year < 2000 || year > 2100) throw new IllegalArgumentException("Choose a year between 2000 and 2100.");
    }

    private static boolean sameName(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static boolean overlaps(PublicHoliday a, PublicHoliday b) {
        return !a.getEndDate().isBefore(b.getStartDate()) && !b.getEndDate().isBefore(a.getStartDate());
    }

    private static PublicHoliday build(Long companyId, String name, LocalDate start, LocalDate end,
                                       PublicHoliday.Category category, boolean recurring,
                                       boolean confirmed, String notes) {
        PublicHoliday h = new PublicHoliday();
        h.setCompanyId(companyId);
        h.setName(name);
        h.setStartDate(start);
        h.setEndDate(end);
        h.setCategory(category);
        h.setRecurring(recurring);
        h.setConfirmed(confirmed);
        h.setNotes(notes);
        return h;
    }

    /**
     * Gregorian date of Hijri {@code month}/{@code day} falling in Gregorian {@code year},
     * using the Umm al-Qura calendar. Empty outside the calendar's supported range.
     */
    private static Optional<LocalDate> estimateHijri(int year, int month, int day) {
        try {
            int hijriYear = HijrahDate.from(LocalDate.of(year, 1, 1)).get(ChronoField.YEAR);
            for (int hy = hijriYear; hy <= hijriYear + 1; hy++) {
                LocalDate g = LocalDate.from(HijrahChronology.INSTANCE.date(hy, month, day));
                if (g.getYear() == year) return Optional.of(g);
            }
        } catch (Exception ignored) {
            // outside the supported Hijri range
        }
        return Optional.empty();
    }

    /** Re-estimate a moving (Hijri-based) holiday into {@code toYear} using its Hijri month/day. */
    private static Optional<LocalDate> reestimate(LocalDate source, int toYear) {
        try {
            HijrahDate h = HijrahDate.from(source);
            return estimateHijri(toYear, h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private PublicHolidayDTO toDTO(PublicHoliday h) {
        int total = (int) ChronoUnit.DAYS.between(h.getStartDate(), h.getEndDate()) + 1;
        return PublicHolidayDTO.builder()
                .id(h.getId())
                .name(h.getName())
                .startDate(h.getStartDate())
                .endDate(h.getEndDate())
                .category(h.getCategory() != null ? h.getCategory().name() : null)
                .recurring(h.isRecurring())
                .confirmed(h.isConfirmed())
                .notes(h.getNotes())
                .totalDays(total)
                .workingDays(LeaveAttendanceUtil.countWorkingDays(h.getStartDate(), h.getEndDate()))
                .build();
    }
}
