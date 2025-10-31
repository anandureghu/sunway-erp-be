package com.hrmodule.dto.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;

// If you use Lombok, you can replace all getters/setters with @lombok.Data
public class PayrollRequest {

    // ---- Required on CREATE ----
    private Long employeeId;

    // Period & days
    private String payrollCode;
    private LocalDate payPeriod;        // period start (or month marker)
    private LocalDate payPeriodEnd;     // period end
    private Integer workingPeriod;      // total working days in period (e.g., 30)
    private Integer payDays;            // payable days (e.g., 26)

    // Compensation inputs
    private BigDecimal basic;                 // monthly basic
    private String transportation;            // "Yes" or "No"
    private BigDecimal conveyanceAllowance;   // transport allowance (if any)
    private BigDecimal travel;                // travel allowance
    private BigDecimal otherCompensationAllowable;

    private String compensationStatus;        // Active/Inactive
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    // Bank & location
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

    // ---- Getters & Setters ----
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
}
