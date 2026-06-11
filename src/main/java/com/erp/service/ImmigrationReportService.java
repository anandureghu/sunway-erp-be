package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.Passport;
import com.erp.domain.ResidencePermit;
import com.erp.dto.immigration.ImmigrationExpiryItemDTO;
import com.erp.repo.PassportRepository;
import com.erp.repo.ResidencePermitRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Company-wide immigration expiry reporting — surfaces passports and residence
 * permits that are expired or expiring within a window, so HR can act before a
 * document lapses. Scoped to the caller's company.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImmigrationReportService {

    private final PassportRepository passportRepo;
    private final ResidencePermitRepository permitRepo;
    private final AuthContext authContext;

    public List<ImmigrationExpiryItemDTO> getExpiring(int withinDays) {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("No company context for the current user");
        }

        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(Math.max(0, withinDays));

        List<ImmigrationExpiryItemDTO> items = new ArrayList<>();

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

        // Soonest-to-expire (and already-expired) first.
        items.sort(Comparator.comparing(ImmigrationExpiryItemDTO::getExpiryDate));
        return items;
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
