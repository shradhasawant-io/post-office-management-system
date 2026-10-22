package com.postoffice.agent.service;

import com.postoffice.agent.dto.report.CollectionReportResponse;

import java.time.LocalDate;

public interface ReportService {

    CollectionReportResponse getDailyReport(LocalDate date);

    CollectionReportResponse getMonthlyReport(int year, int month);
}