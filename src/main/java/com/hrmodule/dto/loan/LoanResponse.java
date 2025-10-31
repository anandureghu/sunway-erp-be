package com.hrmodule.dto.loan;

import com.hrmodule.domain.Loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public class LoanResponse {
    private Long id;
    private Long employeeId;

    private String loanCode, loanType, loanStatus, loanPeriod, notes;
    private LocalDate startDate;
    private BigDecimal loanAmount, monthlyDeductions, balance;

    private String itemCode, itemName, itemStatus, itemDescription;
    private LocalDate dateGiven, returnDate;

    // getters/setters
    // (generate all)
    // ...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getLoanCode() { return loanCode; }
    public void setLoanCode(String loanCode) { this.loanCode = loanCode; }
    public String getLoanType() { return loanType; }
    public void setLoanType(String loanType) { this.loanType = loanType; }
    public String getLoanStatus() { return loanStatus; }
    public void setLoanStatus(String loanStatus) { this.loanStatus = loanStatus; }
    public String getLoanPeriod() { return loanPeriod; }
    public void setLoanPeriod(String loanPeriod) { this.loanPeriod = loanPeriod; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public BigDecimal getLoanAmount() { return loanAmount; }
    public void setLoanAmount(BigDecimal loanAmount) { this.loanAmount = loanAmount; }
    public BigDecimal getMonthlyDeductions() { return monthlyDeductions; }
    public void setMonthlyDeductions(BigDecimal monthlyDeductions) { this.monthlyDeductions = monthlyDeductions; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getItemStatus() { return itemStatus; }
    public void setItemStatus(String itemStatus) { this.itemStatus = itemStatus; }
    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }
    public LocalDate getDateGiven() { return dateGiven; }
    public void setDateGiven(LocalDate dateGiven) { this.dateGiven = dateGiven; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }

    public static LoanResponse from(Loan l) {
        LoanResponse r = new LoanResponse();
        r.setId(l.getId());
        r.setEmployeeId(l.getEmployee().getId());
        r.setLoanCode(l.getLoanCode());
        r.setLoanType(l.getLoanType());
        r.setStartDate(l.getStartDate());
        r.setLoanStatus(l.getLoanStatus());
        r.setLoanAmount(l.getLoanAmount());
        r.setLoanPeriod(l.getLoanPeriod());
        r.setMonthlyDeductions(l.getMonthlyDeductions());
        r.setBalance(l.getBalance());
        r.setNotes(l.getNotes());

        r.setItemCode(l.getItemCode());
        r.setItemName(l.getItemName());
        r.setItemStatus(l.getItemStatus());
        r.setDateGiven(l.getDateGiven());
        r.setReturnDate(l.getReturnDate());
        r.setItemDescription(l.getItemDescription());
        return r;
    }
}
