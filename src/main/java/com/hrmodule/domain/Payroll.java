// com.hrmodule.domain.Payroll
package com.hrmodule.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payrolls")
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private String payrollCode;
    private LocalDate payPeriod;
    private LocalDate payPeriodEnd;
    private Integer workingPeriod;
    private Integer payDays;

    // store the monthly basic in DB
    @Column(precision = 18, scale = 2)
    private BigDecimal monthlyBasic;

    private String transportation; // "Yes"/"No"
    @Column(precision = 18, scale = 2) private BigDecimal conveyanceAllowance;
    @Column(precision = 18, scale = 2) private BigDecimal travel;
    @Column(precision = 18, scale = 2) private BigDecimal otherCompensationAllowable;

    private String compensationStatus; // Active/Inactive
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    private String bankName, accountNo, accountType, bankBranch, bankRemarks;
    private String location, street, city, state, country, iban;

    // derived
    @Column(precision = 18, scale = 2) private BigDecimal basicProrated;
    @Column(precision = 18, scale = 2) private BigDecimal totalAllowances;
    @Column(precision = 18, scale = 2) private BigDecimal netPayable;

    // --- getters / setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

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

    public BigDecimal getMonthlyBasic() { return monthlyBasic; }
    public void setMonthlyBasic(BigDecimal monthlyBasic) { this.monthlyBasic = monthlyBasic; }

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
}
