package com.erp.dto.timesheet;

import java.util.List;

public class TimesheetDashboardResponse {

    private MonthlySummaryResponse summary;
    private TimesheetTodayResponse today;
    private List<AttendanceHistoryItemResponse> attendanceHistory;

    public MonthlySummaryResponse getSummary() {
        return summary;
    }

    public void setSummary(MonthlySummaryResponse summary) {
        this.summary = summary;
    }

    public TimesheetTodayResponse getToday() {
        return today;
    }

    public void setToday(TimesheetTodayResponse today) {
        this.today = today;
    }

    public List<AttendanceHistoryItemResponse> getAttendanceHistory() {
        return attendanceHistory;
    }

    public void setAttendanceHistory(List<AttendanceHistoryItemResponse> attendanceHistory) {
        this.attendanceHistory = attendanceHistory;
    }
}