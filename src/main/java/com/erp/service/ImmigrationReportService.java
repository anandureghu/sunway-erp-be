package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.Passport;
import com.erp.domain.ResidencePermit;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.immigration.ImmigrationExpiryItemDTO;
import com.erp.repo.PassportRepository;
import com.erp.repo.ResidencePermitRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.security.PermissionCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Immigration expiry reporting — surfaces passports and residence permits that
 * are expired or expiring within a window, so HR can act before a document
 * lapses. Always scoped to the caller's company; the row scope follows the
 * caller's IMMIGRATION grant: VIEW_ALL sees every employee, VIEW_OWN sees only
 * their own documents.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImmigrationReportService {

    private final PassportRepository passportRepo;
    private final ResidencePermitRepository permitRepo;
    private final AuthContext authContext;
    private final PermissionCheckService permissionCheck;

    public List<ImmigrationExpiryItemDTO> getExpiring(int withinDays) {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("No company context for the current user");
        }

        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(Math.max(0, withinDays));

        List<ImmigrationExpiryItemDTO> items = new ArrayList<>();

        if (canViewAll()) {
            // Company-wide view (admins / HR / anyone granted IMMIGRATION VIEW_ALL).
            for (Passport p : passportRepo
                    .findByEmployee_Company_IdAndExpiryDateLessThanEqual(companyId, cutoff)) {
                items.add(toItem("PASSPORT", p.getEmployee(), p.getPassportNo(),
                        p.getExpiryDate(), today));
            }

            for (ResidencePermit rp : permitRepo
                    .findByEmployee_Company_IdAndEndDateLessThanEqual(companyId, cutoff)) {
                items.add(toItem("RESIDENCE_PERMIT", rp.getEmployee(), rp.getPermitIdNumber(),
                        rp.getEndDate(), today));
            }
        } else {
            // Own-only view: the caller (VIEW_OWN but not VIEW_ALL) sees just
            // their own passport / residence permit if it falls in the window.
            Long employeeId = authContext.getCurrentEmployeeId();
            if (employeeId == null) {
                return List.of();
            }

            passportRepo.findByEmployeeId(employeeId)
                    .filter(p -> sameCompany(p.getEmployee(), companyId))
                    .filter(p -> p.getExpiryDate() != null && !p.getExpiryDate().isAfter(cutoff))
                    .ifPresent(p -> items.add(toItem("PASSPORT", p.getEmployee(),
                            p.getPassportNo(), p.getExpiryDate(), today)));

            permitRepo.findByEmployeeId(employeeId)
                    .filter(rp -> sameCompany(rp.getEmployee(), companyId))
                    .filter(rp -> rp.getEndDate() != null && !rp.getEndDate().isAfter(cutoff))
                    .ifPresent(rp -> items.add(toItem("RESIDENCE_PERMIT", rp.getEmployee(),
                            rp.getPermitIdNumber(), rp.getEndDate(), today)));
        }

        // Soonest-to-expire (and already-expired) first.
        items.sort(Comparator.comparing(ImmigrationExpiryItemDTO::getExpiryDate));
        return items;
    }

    /** Caller can see every employee's documents (admin bypass or VIEW_ALL grant). */
    private boolean canViewAll() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return permissionCheck.hasAccess(auth, AppModule.IMMIGRATION, AppAction.VIEW_ALL);
    }

    private boolean sameCompany(Employee emp, Long companyId) {
        return emp != null && emp.getCompany() != null
                && companyId.equals(emp.getCompany().getId());
    }

    private ImmigrationExpiryItemDTO toItem(
            String type, Employee emp, String documentNumber, LocalDate expiry, LocalDate today) {

        long daysRemaining = ChronoUnit.DAYS.between(today, expiry);
        return ImmigrationExpiryItemDTO.builder()
                .documentType(type)
                .employeeId(emp != null ? emp.getId() : null)
                .employeeCode(emp != null ? emp.getEmployeeNo() : null)
                .employeeName(emp == null ? null
                        : ((emp.getFirstName() == null ? "" : emp.getFirstName())
                            + " " + (emp.getLastName() == null ? "" : emp.getLastName())).trim())
                .documentNumber(documentNumber)
                .expiryDate(expiry)
                .daysRemaining(daysRemaining)
                .status(daysRemaining < 0 ? "EXPIRED" : "EXPIRING_SOON")
                .build();
    }
}
