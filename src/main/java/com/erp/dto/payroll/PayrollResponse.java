package com.erp.dto.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PayrollResponse {
    private Long id;
    private Long employeeId;
    private String payrollCode;
    private LocalDate payPeriod;
    private LocalDate payPeriodEnd;
    private Integer workingPeriod;
    private Integer payDays;

    private BigDecimal basic; // monthly basic
    private String transportation; // "Yes"/"No"
    private BigDecimal conveyanceAllowance;
    private BigDecimal travel;
    private BigDecimal otherCompensationAllowable;

    private String compensationStatus;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    private String bankName;
    private String accountNo;
    private String accountType;
    private String bankBranch;
    private String bankRemarks;
    private String location;
    private String street;
    private String city;
    private String state;
    private String country;
    private String iban;

    // derived
    private BigDecimal basicProrated;
    private BigDecimal totalAllowances;
    private BigDecimal netPayable;

    // ---- Getters & Setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getPayrollCode() { return payrollCode; }
    public void setPayrollCode(String payrollCode) { this.payrollCode = payrollCode; }

    public LocalDate getPayPeriod() { return payPeriod; }
    public void setPayPeriod(LocalDate payPeriod) { this.payPeriod = payPeriod; }

    public LocalDate getPayPeriodEnd() { return payPeriodEnd; }
    public void setPayPeriodEnd(LocalDate payPeriodEnd) { this.payPeriodEnd = payPeriodEnd; }

    public Integer getWorkingPeriod() { return workingPeriod; }
    public void setWorkingPeriod(Integer workingPeriod) { this.workingPeriod = workingPeriod; }

    public Integer getPayDays() { return payDays; }
    public void setPayDays(Integer payDays) { this.payDays = payDays; }

    public BigDecimal getBasic() { return basic; }
    public void setBasic(BigDecimal basic) { this.basic = basic; }

    public String getTransportation() { return transportation; }
    public void setTransportation(String transportation) { this.transportation = transportation; }

    public BigDecimal getConveyanceAllowance() { return conveyanceAllowance; }
    public void setConveyanceAllowance(BigDecimal conveyanceAllowance) { this.conveyanceAllowance = conveyanceAllowance; }

    public BigDecimal getTravel() { return travel; }
    public void setTravel(BigDecimal travel) { this.travel = travel; }

    public BigDecimal getOtherCompensationAllowable() { return otherCompensationAllowable; }
    public void setOtherCompensationAllowable(BigDecimal otherCompensationAllowable) { this.otherCompensationAllowable = otherCompensationAllowable; }

    public String getCompensationStatus() { return compensationStatus; }
    public void setCompensationStatus(String compensationStatus) { this.compensationStatus = compensationStatus; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getBankBranch() { return bankBranch; }
    public void setBankBranch(String bankBranch) { this.bankBranch = bankBranch; }

    public String getBankRemarks() { return bankRemarks; }
    public void setBankRemarks(String bankRemarks) { this.bankRemarks = bankRemarks; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }

    public BigDecimal getBasicProrated() { return basicProrated; }
    public void setBasicProrated(BigDecimal basicProrated) { this.basicProrated = basicProrated; }

    public BigDecimal getTotalAllowances() { return totalAllowances; }
    public void setTotalAllowances(BigDecimal totalAllowances) { this.totalAllowances = totalAllowances; }

    public BigDecimal getNetPayable() { return netPayable; }
    public void setNetPayable(BigDecimal netPayable) { this.netPayable = netPayable; }

    // ---- Mapper ----
    public static PayrollResponse from(com.erp.domain.Payroll p) {
        PayrollResponse r = new PayrollResponse();
        r.setId(p.getId());
        r.setEmployeeId(p.getEmployee().getId());
        r.setPayrollCode(p.getPayrollCode());
        r.setPayPeriod(p.getPayPeriod());
        r.setPayPeriodEnd(p.getPayPeriodEnd());
        r.setWorkingPeriod(p.getWorkingPeriod());
        r.setPayDays(p.getPayDays());
        r.setBasic(p.getMonthlyBasic());
        r.setTransportation(p.getTransportation());
        r.setConveyanceAllowance(p.getConveyanceAllowance());
        r.setTravel(p.getTravel());
        r.setOtherCompensationAllowable(p.getOtherCompensationAllowable());
        r.setCompensationStatus(p.getCompensationStatus());
        r.setEffectiveFrom(p.getEffectiveFrom());
        r.setEffectiveTo(p.getEffectiveTo());
        r.setBankName(p.getBankName());
        r.setAccountNo(p.getAccountNo());
        r.setAccountType(p.getAccountType());
        r.setBankBranch(p.getBankBranch());
        r.setBankRemarks(p.getBankRemarks());
        r.setLocation(p.getLocation());
        r.setStreet(p.getStreet());
        r.setCity(p.getCity());
        r.setState(p.getState());
        r.setCountry(p.getCountry());
        r.setIban(p.getIban());
        r.setBasicProrated(p.getBasicProrated());
        r.setTotalAllowances(p.getTotalAllowances());
        r.setNetPayable(p.getNetPayable());
        return r;
    }
}
