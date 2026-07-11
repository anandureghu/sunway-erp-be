package com.erp.service;

import com.erp.domain.AttendanceMonthArchive;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.timesheet.AttendanceArchiveRowDTO;
import com.erp.dto.timesheet.AttendanceHistoryPageDTO;
import com.erp.dto.timesheet.EmployeeMonthlyAttendanceDTO;
import com.erp.repo.AttendanceMonthArchiveRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.security.PermissionCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Month-archive lifecycle for attendance. Archiving snapshots the company-wide
 * worked-days rollup for a month into {@link AttendanceMonthArchive}. Gated by
 * the HR_REPORTS grant — a regular employee cannot archive or read archives.
 */
@Service
@RequiredArgsConstructor
public class AttendanceArchiveService {

    private final AttendanceReportService reportService;
    private final AttendanceMonthArchiveRepository archiveRepo;
    private final AuthContext authContext;
    private final PermissionCheckService permissionCheck;

    /** Snapshot every employee's worked-days for the month. Returns rows written. */
    @Transactional
    public int archiveMonth(int year, int month) {
        Long companyId = requireHrCompany();

        if (archiveRepo.existsByCompanyIdAndPeriodYearAndPeriodMonth(companyId, year, month)) {
            throw new IllegalStateException(
                    "This month is already archived. Unarchive it first to re-snapshot.");
        }

        List<EmployeeMonthlyAttendanceDTO> summary =
                reportService.computeCompanySummary(companyId, year, month);
        if (summary.isEmpty()) {
            throw new IllegalStateException("No attendance to archive for the selected month.");
        }

        Long userId = authContext.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();

        List<AttendanceMonthArchive> rows = summary.stream().map(s -> {
            AttendanceMonthArchive a = new AttendanceMonthArchive();
            a.setCompanyId(companyId);
            a.setEmployeeId(s.getEmployeeId());
            a.setEmployeeNo(s.getEmployeeNo());
            a.setEmployeeName(s.getEmployeeName());
            a.setDepartment(s.getDepartment());
            a.setPeriodYear(year);
            a.setPeriodMonth(month);
            a.setDaysRecorded(s.getDaysRecorded());
            a.setDaysPresent(s.getDaysPresent());
            a.setTotalHours(s.getTotalHours());
            a.setArchivedAt(now);
            a.setArchivedBy(userId);
            return a;
        }).toList();

        archiveRepo.saveAll(rows);
        return rows.size();
    }

    @Transactional
    public void unarchiveMonth(int year, int month) {
        Long companyId = requireHrCompany();
        archiveRepo.deleteByCompanyIdAndPeriodYearAndPeriodMonth(companyId, year, month);
    }

    /**
     * Paged worked-days history for a month. Serves the frozen snapshot when the
     * month is archived (true DB paging), otherwise the live computed figures
     * (computed company-wide, then sliced for the page).
     */
    @Transactional(readOnly = true)
    public AttendanceHistoryPageDTO getMonthlyHistory(
            int year, int month, String employeeCode, int page, int size) {
        Long companyId = requireHrCompany();
        String code = (employeeCode == null || employeeCode.isBlank()) ? null : employeeCode.trim();
        int safeSize = size <= 0 ? 10 : Math.min(size, 200);
        int safePage = Math.max(page, 0);

        if (archiveRepo.existsByCompanyIdAndPeriodYearAndPeriodMonth(companyId, year, month)) {
            Pageable pageable = PageRequest.of(safePage, safeSize);
            Page<AttendanceMonthArchive> pg =
                    archiveRepo.searchPaged(companyId, year, month, code, pageable);
            return AttendanceHistoryPageDTO.builder()
                    .content(pg.getContent().stream().map(this::toRow).toList())
                    .page(pg.getNumber())
                    .size(pg.getSize())
                    .totalElements(pg.getTotalElements())
                    .totalPages(pg.getTotalPages())
                    .archived(true)
                    .build();
        }

        // Live: compute the full company summary, filter by code, slice the page.
        List<EmployeeMonthlyAttendanceDTO> all =
                reportService.computeCompanySummary(companyId, year, month);
        if (code != null) {
            String lc = code.toLowerCase();
            all = all.stream()
                    .filter(r ->
                            (r.getEmployeeNo() != null && r.getEmployeeNo().toLowerCase().contains(lc))
                            || (r.getEmployeeName() != null && r.getEmployeeName().toLowerCase().contains(lc)))
                    .toList();
        }
        long total = all.size();
        int totalPages = Math.max((int) Math.ceil(total / (double) safeSize), 1);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());

        return AttendanceHistoryPageDTO.builder()
                .content(List.copyOf(all.subList(from, to)))
                .page(safePage)
                .size(safeSize)
                .totalElements(total)
                .totalPages(totalPages)
                .archived(false)
                .build();
    }

    private EmployeeMonthlyAttendanceDTO toRow(AttendanceMonthArchive a) {
        return EmployeeMonthlyAttendanceDTO.builder()
                .employeeId(a.getEmployeeId())
                .employeeNo(a.getEmployeeNo())
                .employeeName(a.getEmployeeName())
                .department(a.getDepartment())
                .daysRecorded(a.getDaysRecorded())
                .daysPresent(a.getDaysPresent())
                .totalHours(a.getTotalHours())
                .todayStatus("NOT_CHECKED_IN")
                .build();
    }

    @Transactional(readOnly = true)
    public List<AttendanceArchiveRowDTO> listArchived(Integer year, Integer month, String employeeCode) {
        Long companyId = requireHrCompany();
        String code = (employeeCode == null || employeeCode.isBlank()) ? null : employeeCode.trim();
        return archiveRepo.search(companyId, year, month, code).stream()
                .map(this::toDTO)
                .toList();
    }

    /** Resolve the company and assert the caller may manage/view HR attendance archives. */
    private Long requireHrCompany() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("No company context for the current user");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!permissionCheck.hasAny(auth, AppModule.HR_REPORTS, AppAction.VIEW_ALL, AppAction.VIEW_OWN)) {
            throw new AccessDeniedException("HR reports permission is required to manage attendance archives");
        }
        return companyId;
    }

    private AttendanceArchiveRowDTO toDTO(AttendanceMonthArchive a) {
        return AttendanceArchiveRowDTO.builder()
                .id(a.getId())
                .employeeId(a.getEmployeeId())
                .employeeNo(a.getEmployeeNo())
                .employeeName(a.getEmployeeName())
                .department(a.getDepartment())
                .periodYear(a.getPeriodYear())
                .periodMonth(a.getPeriodMonth())
                .daysRecorded(a.getDaysRecorded())
                .daysPresent(a.getDaysPresent())
                .totalHours(a.getTotalHours())
                .archivedAt(a.getArchivedAt())
                .build();
    }
}
