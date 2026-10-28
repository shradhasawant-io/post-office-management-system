package com.postoffice.agent.service;

import com.postoffice.agent.dto.report.CollectionReportResponse;
import com.postoffice.agent.enums.PaymentMode;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ReportService {

    CollectionReportResponse getDailyReport(LocalDate date);

    CollectionReportResponse getMonthlyReport(int year, int month);

    CollectionReportResponse searchReports(
            String accountNumber,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal amount,
            PaymentMode paymentMode
    );
}