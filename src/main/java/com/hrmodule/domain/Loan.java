package com.hrmodule.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "loans")
public class Loan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    // Loan info
    @Column(length = 50)  private String loanCode;
    @Column(length = 50)  private String loanType;
    private LocalDate startDate;
    @Column(length = 50)  private String loanStatus;
    private BigDecimal loanAmount;
    @Column(length = 50)  private String loanPeriod;
    private BigDecimal monthlyDeductions;
    private BigDecimal balance;
    @Column(length = 1000) private String notes;

    // Company property info (on the same screen)
    @Column(length = 50)  private String itemCode;
    @Column(length = 150) private String itemName;
    @Column(length = 50)  private String itemStatus;
    private LocalDate dateGiven;
    private LocalDate returnDate;
    @Column(length = 1000) private String itemDescription;

    // getters & setters
    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public String getLoanCode() { return loanCode; }
    public void setLoanCode(String loanCode) { this.loanCode = loanCode; }
    public String getLoanType() { return loanType; }
    public void setLoanType(String loanType) { this.loanType = loanType; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public String getLoanStatus() { return loanStatus; }
    public void setLoanStatus(String loanStatus) { this.loanStatus = loanStatus; }
    public BigDecimal getLoanAmount() { return loanAmount; }
    public void setLoanAmount(BigDecimal loanAmount) { this.loanAmount = loanAmount; }
    public String getLoanPeriod() { return loanPeriod; }
    public void setLoanPeriod(String loanPeriod) { this.loanPeriod = loanPeriod; }
    public BigDecimal getMonthlyDeductions() { return monthlyDeductions; }
    public void setMonthlyDeductions(BigDecimal monthlyDeductions) { this.monthlyDeductions = monthlyDeductions; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getItemStatus() { return itemStatus; }
    public void setItemStatus(String itemStatus) { this.itemStatus = itemStatus; }
    public LocalDate getDateGiven() { return dateGiven; }
    public void setDateGiven(LocalDate dateGiven) { this.dateGiven = dateGiven; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }
}
