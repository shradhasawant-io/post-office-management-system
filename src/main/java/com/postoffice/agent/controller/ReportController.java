
package com.postoffice.agent.controller;

import com.postoffice.agent.dto.report.CollectionReportResponse;
import com.postoffice.agent.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/daily")
    @PreAuthorize("hasRole('ADMIN')")
    public CollectionReportResponse getDailyReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {
        return reportService.getDailyReport(date);
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasRole('ADMIN')")
    public CollectionReportResponse getMonthlyReport(@RequestParam int year, @RequestParam int month) {
        return reportService.getMonthlyReport(year, month);
    }
}
