// src/main/java/com/hrmodule/domain/CurrentJob.java
package com.erp.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "current_jobs")
public class CurrentJob {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    // scalar fields
    @Column(length = 100,nullable = false) private String jobCode;
    @Column(length = 200) private String jobTitle;
    @Column(length = 50,nullable = false)  private String jobLevel;
    @Column(length = 50)  private String grade;
    @Column(length = 50,nullable = false)  private String departmentCode;
    @Column(length = 200) private String departmentName;
     @Column(nullable = false)
     private LocalDate effectiveFrom;
     @Column(nullable = false)
    private LocalDate startDate;
    private LocalDate expectedEndDate;

    // Experiences
    @ElementCollection
    @CollectionTable(name = "current_job_experiences", joinColumns = @JoinColumn(name = "current_job_id"))
    private List<PreviousExperience> previousExperiences = new ArrayList<>();

    @Embeddable
    public static class PreviousExperience {
        private String previousCompany;
        private String lastJobTitle;
        private LocalDate lastDateWorked;
        private String numberOfYears;

        public String getPreviousCompany() { return previousCompany; }
        public void setPreviousCompany(String v) { this.previousCompany = v; }
        public String getLastJobTitle() { return lastJobTitle; }
        public void setLastJobTitle(String v) { this.lastJobTitle = v; }
        public LocalDate getLastDateWorked() { return lastDateWorked; }
        public void setLastDateWorked(LocalDate v) { this.lastDateWorked = v; }
        public String getNumberOfYears() { return numberOfYears; }
        public void setNumberOfYears(String v) { this.numberOfYears = v; }
    }

    // Educations
    @ElementCollection
    @CollectionTable(name = "current_job_educations", joinColumns = @JoinColumn(name = "current_job_id"))
    private List<Education> educations = new ArrayList<>();

    @Embeddable
    public static class Education {
        private String schoolName;
        private String yearGraduated;
        private String degreeEarned;
        private String awardsCertificates;
        private String major;
        private String notes;

        public String getSchoolName() { return schoolName; }
        public void setSchoolName(String v) { this.schoolName = v; }
        public String getYearGraduated() { return yearGraduated; }
        public void setYearGraduated(String v) { this.yearGraduated = v; }
        public String getDegreeEarned() { return degreeEarned; }
        public void setDegreeEarned(String v) { this.degreeEarned = v; }
        public String getAwardsCertificates() { return awardsCertificates; }
        public void setAwardsCertificates(String v) { this.awardsCertificates = v; }
        public String getMajor() { return major; }
        public void setMajor(String v) { this.major = v; }
        public String getNotes() { return notes; }
        public void setNotes(String v) { this.notes = v; }
    }

    // getters/setters
    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public String getJobCode() { return jobCode; }
    public void setJobCode(String jobCode) { this.jobCode = jobCode; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public String getJobLevel() { return jobLevel; }
    public void setJobLevel(String jobLevel) { this.jobLevel = jobLevel; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getExpectedEndDate() { return expectedEndDate; }
    public void setExpectedEndDate(LocalDate expectedEndDate) { this.expectedEndDate = expectedEndDate; }
    public List<PreviousExperience> getPreviousExperiences() { return previousExperiences; }
    public List<Education> getEducations() { return educations; }
}
