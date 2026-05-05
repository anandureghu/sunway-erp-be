package com.erp.dto.timesheet;

public class MonthlySummaryResponse {

    private int daysPresent;
    private double totalHours;
    private double avgHoursPerDay;
    private int daysRecorded;

    public int getDaysPresent() {
        return daysPresent;
    }

    public void setDaysPresent(int daysPresent) {
        this.daysPresent = daysPresent;
    }

    public double getTotalHours() {
        return totalHours;
    }

    public void setTotalHours(double totalHours) {
        this.totalHours = totalHours;
    }

    public double getAvgHoursPerDay() {
        return avgHoursPerDay;
    }

    public void setAvgHoursPerDay(double avgHoursPerDay) {
        this.avgHoursPerDay = avgHoursPerDay;
    }

    public int getDaysRecorded() {
        return daysRecorded;
    }

    public void setDaysRecorded(int daysRecorded) {
        this.daysRecorded = daysRecorded;
    }
}