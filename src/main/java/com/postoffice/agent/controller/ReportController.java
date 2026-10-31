package com.postoffice.agent.controller;

import com.postoffice.agent.dto.report.CollectionReportResponse;
import com.postoffice.agent.enums.PaymentMode;
import com.postoffice.agent.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }


    // GET DAILY REPORTS
    @GetMapping("/daily")
    @PreAuthorize("hasRole('AGENT')")
    public CollectionReportResponse getDailyReport(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        return reportService.getDailyReport(date);
    }

    // GET MONTHLY REPORTS
    @GetMapping("/monthly")
    @PreAuthorize("hasRole('AGENT')")
    public CollectionReportResponse getMonthlyReport(
            @RequestParam int year,
            @RequestParam int month) {

        return reportService.getMonthlyReport(year, month);
    }

    // Search/filter reports for the authenticated agent
    // IMPORTANT:
    // AgentId is deliberately NOT accepted from the client
    // The authenticated agent ID is obtained inside the service from Spring Security
    @GetMapping("/search")
    @PreAuthorize("hasRole('AGENT')")
    public CollectionReportResponse searchReports(
            @RequestParam(required = false)
            String accountNumber,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false)
            BigDecimal amount,

            @RequestParam(required = false)
            PaymentMode paymentMode) {

        return reportService.searchReports(
                accountNumber,
                startDate,
                endDate,
                amount,
                paymentMode
        );
    }
}