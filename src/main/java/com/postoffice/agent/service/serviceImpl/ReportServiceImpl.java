
package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.dto.report.CollectionReportResponse;
import com.postoffice.agent.dto.report.CollectionTransactionResponse;
import com.postoffice.agent.entity.Transaction;
import com.postoffice.agent.enums.TransactionType;
import com.postoffice.agent.exception.BusinessException;
import com.postoffice.agent.repository.TransactionRepository;
import com.postoffice.agent.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    private final TransactionRepository transactionRepository;

    public ReportServiceImpl(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionReportResponse getDailyReport(LocalDate date) {
        if (date == null) {
            throw new BusinessException("Report date is required");
        }

        LocalDate endDate = date.plusDays(1);
        return buildReport("DAILY", date, date, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionReportResponse getMonthlyReport(int year, int month) {
        YearMonth yearMonth;

        try {
            yearMonth = YearMonth.of(year, month);
        } catch (DateTimeException exception) {
            throw new BusinessException("Invalid year or month. Provide a valid year and month.");
        }

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.plusMonths(1).atDay(1);

        return buildReport("MONTHLY", startDate, yearMonth.atEndOfMonth(), endDate);
    }

    private CollectionReportResponse buildReport(String reportType, LocalDate startDate, LocalDate displayedEndDate, LocalDate exclusiveEndDate) {

        List<Transaction> deposits = transactionRepository.findDepositsInDateRange(TransactionType.DEPOSIT, startDate, exclusiveEndDate);

        List<CollectionTransactionResponse> details = deposits
                .stream()
                .map(transaction ->
                        CollectionTransactionResponse.builder()
                                .transactionId(transaction.getTransactionId())
                                .accountId(transaction.getTransactionAccountId().getAccountId())
                                .transactionDate(transaction.getTransactionDate())
                                .amount(transaction.getTransactionAmount())
                                .paymentMode(transaction.getPaymentMode())
                                .remarks(transaction.getRemarks())
                                .build())
                .toList();

        BigDecimal totalCollection = deposits.stream()
                .map(Transaction::getTransactionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info(
                "{} collection report generated. startDate={}, endDate={}, transactions={}",
                reportType,
                startDate,
                displayedEndDate,
                deposits.size()
        );

        return CollectionReportResponse.builder()
                .reportType(reportType)
                .startDate(startDate)
                .endDate(displayedEndDate)
                .totalTransactions(deposits.size())
                .totalCollection(totalCollection)
                .transactions(details)
                .build();
    }
}
