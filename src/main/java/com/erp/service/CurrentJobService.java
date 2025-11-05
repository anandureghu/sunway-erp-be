// src/main/java/com/hrmodule/service/CurrentJobService.java
package com.erp.service;

import com.erp.domain.CurrentJob;
import com.erp.domain.Employee;
import com.erp.dto.currentjob.CurrentJobRequest;
import com.erp.dto.currentjob.CurrentJobResponse;
import com.erp.repo.CurrentJobRepository;
import com.erp.repo.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class CurrentJobService {

    private final CurrentJobRepository repo;
    private final EmployeeRepository employeeRepo;

    public CurrentJobService(CurrentJobRepository repo, EmployeeRepository employeeRepo) {
        this.repo = repo;
        this.employeeRepo = employeeRepo;
    }

    @Transactional(readOnly = true)
    public CurrentJobResponse getForEmployee(Long empId) {
        Employee emp = employeeRepo.findById(empId).orElseThrow();
        CurrentJob cj = repo.findByEmployee(emp).orElseGet(() -> {
            CurrentJob n = new CurrentJob();
            n.setEmployee(emp);
            return n; // not saved yet; empty response is fine
        });
        return toResponse(cj, null);
    }

    @Transactional
    public CurrentJobResponse upsertForEmployee(Long empId, CurrentJobRequest req) {
        Employee emp = employeeRepo.findById(empId).orElseThrow();

        CurrentJob cj = repo.findByEmployee(emp).orElseGet(() -> {
            CurrentJob n = new CurrentJob();
            n.setEmployee(emp);
            return n;
        });

        // Use a final reference inside lambdas/method refs
        final CurrentJob entity = cj;

        Map<String, Object> changes = new LinkedHashMap<>();

        // Scalars – patch semantics (only apply when new value provided; record change if actually different)
        change(changes, "jobCode",         entity.getJobCode(),         req.jobCode,         entity::setJobCode);
        change(changes, "jobTitle",        entity.getJobTitle(),        req.jobTitle,        entity::setJobTitle);
        change(changes, "jobLevel",        entity.getJobLevel(),        req.jobLevel,        entity::setJobLevel);
        change(changes, "grade",           entity.getGrade(),           req.grade,           entity::setGrade);
        change(changes, "departmentCode",  entity.getDepartmentCode(),  req.departmentCode,  entity::setDepartmentCode);
        change(changes, "departmentName",  entity.getDepartmentName(),  req.departmentName,  entity::setDepartmentName);
        change(changes, "effectiveFrom",   entity.getEffectiveFrom(),   req.effectiveFrom,   entity::setEffectiveFrom);
        change(changes, "startDate",       entity.getStartDate(),       req.startDate,       entity::setStartDate);
        change(changes, "expectedEndDate", entity.getExpectedEndDate(), req.expectedEndDate, entity::setExpectedEndDate);

        // Lists – only replace if present in payload
        if (req.previousExperiences != null) {
            int beforeSize = entity.getPreviousExperiences().size();
            entity.getPreviousExperiences().clear();
            req.previousExperiences.forEach(pe -> {
                var emb = new CurrentJob.PreviousExperience();
                emb.setPreviousCompany(pe.previousCompany);
                emb.setLastJobTitle(pe.lastJobTitle);
                emb.setLastDateWorked(pe.lastDateWorked);
                emb.setNumberOfYears(pe.numberOfYears);
                entity.getPreviousExperiences().add(emb);
            });
            changes.put("previousExperiences",
                    Map.of("oldSize", beforeSize, "newSize", entity.getPreviousExperiences().size()));
        }

        if (req.educations != null) {
            int beforeSize = entity.getEducations().size();
            entity.getEducations().clear();
            req.educations.forEach(ed -> {
                var emb = new CurrentJob.Education();
                emb.setSchoolName(ed.schoolName);
                emb.setYearGraduated(ed.yearGraduated);
                emb.setDegreeEarned(ed.degreeEarned);
                emb.setAwardsCertificates(ed.awardsCertificates);
                emb.setMajor(ed.major);
                emb.setNotes(ed.notes);
                entity.getEducations().add(emb);
            });
            changes.put("educations",
                    Map.of("oldSize", beforeSize, "newSize", entity.getEducations().size()));
        }

        // Persist changes; do NOT reassign the variable used in lambdas
        repo.save(entity);

        return toResponse(entity, changes);
    }

    /**
     * Record a change only when newVal != null and !Objects.equals(oldVal, newVal).
     * If newVal != null but equals old, still set it silently (idempotent patch) but don't record a change.
     * If newVal == null → ignore (patch semantics).
     */
    private static <T> void change(Map<String, Object> changes,
                                   String key,
                                   T oldVal,
                                   T newVal,
                                   java.util.function.Consumer<T> setter) {
        if (newVal != null && !Objects.equals(oldVal, newVal)) {
            changes.put(key, Map.of("old", oldVal, "new", newVal));
            setter.accept(newVal);
        } else if (newVal != null) {
            // client sent same value → accept silently
            setter.accept(newVal);
        }
        // if newVal == null → ignore (patch)
    }

    private CurrentJobResponse toResponse(CurrentJob cj, Map<String, Object> changes) {
        var r = new CurrentJobResponse();
        r.id = cj.getId();
        r.employeeId = (cj.getEmployee() != null) ? cj.getEmployee().getId() : null;
        r.jobCode = cj.getJobCode();
        r.jobTitle = cj.getJobTitle();
        r.jobLevel = cj.getJobLevel();
        r.grade = cj.getGrade();
        r.departmentCode = cj.getDepartmentCode();
        r.departmentName = cj.getDepartmentName();
        r.effectiveFrom = cj.getEffectiveFrom();
        r.startDate = cj.getStartDate();
        r.expectedEndDate = cj.getExpectedEndDate();

        r.previousExperiences = cj.getPreviousExperiences().stream().map(pe -> {
            var d = new CurrentJobRequest.PreviousExperienceDTO();
            d.previousCompany = pe.getPreviousCompany();
            d.lastJobTitle = pe.getLastJobTitle();
            d.lastDateWorked = pe.getLastDateWorked();
            d.numberOfYears = pe.getNumberOfYears();
            return d;
        }).toList();

        r.educations = cj.getEducations().stream().map(ed -> {
            var d = new CurrentJobRequest.EducationDTO();
            d.schoolName = ed.getSchoolName();
            d.yearGraduated = ed.getYearGraduated();
            d.degreeEarned = ed.getDegreeEarned();
            d.awardsCertificates = ed.getAwardsCertificates();
            d.major = ed.getMajor();
            d.notes = ed.getNotes();
            return d;
        }).toList();

        r.changedFields = (changes == null) ? Map.of() : changes;
        return r;
    }
}
