package com.erp.dto.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public class LoanRequest {
    // Use this to decide create (null) vs update (non-null)
    private Long id;

    // same fields the UI sends
    private String loanCode, loanType, loanStatus, loanPeriod, notes;
    private LocalDate startDate;
    private BigDecimal loanAmount, monthlyDeductions, balance;

    private String itemCode, itemName, itemStatus, itemDescription;
    private LocalDate dateGiven, returnDate;

    // ---- getters/setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
}
