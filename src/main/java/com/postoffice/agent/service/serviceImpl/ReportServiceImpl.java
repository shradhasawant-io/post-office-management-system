package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.dto.report.CollectionReportResponse;
import com.postoffice.agent.dto.report.CollectionTransactionResponse;
import com.postoffice.agent.entity.Transaction;
import com.postoffice.agent.enums.PaymentMode;
import com.postoffice.agent.enums.TransactionType;
import com.postoffice.agent.exception.BusinessException;
import com.postoffice.agent.repository.TransactionRepository;
import com.postoffice.agent.security.AuthenticatedAgentService;
import com.postoffice.agent.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final AuthenticatedAgentService authenticatedAgentService;

    public ReportServiceImpl(
            TransactionRepository transactionRepository,
            AuthenticatedAgentService authenticatedAgentService) {

        this.transactionRepository = transactionRepository;
        this.authenticatedAgentService = authenticatedAgentService;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('AGENT')")
    public CollectionReportResponse getDailyReport(LocalDate date) {

        if (date == null) {
            throw new BusinessException("Report date is required");
        }

        LocalDate endDate = date.plusDays(1);

        return buildReport(
                "DAILY",
                date,
                date,
                endDate
        );
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('AGENT')")
    public CollectionReportResponse getMonthlyReport(
            int year,
            int month) {

        YearMonth yearMonth;

        try {
            yearMonth = YearMonth.of(year, month);
        } catch (DateTimeException exception) {
            throw new BusinessException(
                    "Invalid year or month. Provide a valid year and month."
            );
        }

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.plusMonths(1).atDay(1);

        return buildReport(
                "MONTHLY",
                startDate,
                yearMonth.atEndOfMonth(),
                endDate
        );
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('AGENT')")
    public CollectionReportResponse searchReports(
            String accountNumber,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal amount,
            PaymentMode paymentMode) {

        Long authenticatedAgentId =
                authenticatedAgentService.getAuthenticatedAgentId();

        /*
         * Normalize account number.
         */
        if (accountNumber != null) {
            accountNumber = accountNumber.trim();

            if (accountNumber.isBlank()) {
                accountNumber = null;
            }
        }

        /*
         * Validate date range.
         */
        if (startDate != null
                && endDate != null
                && endDate.isBefore(startDate)) {

            throw new BusinessException(
                    "End date cannot be before start date"
            );
        }

        /*
         * Validate amount.
         */
        if (amount != null && amount.signum() < 0) {
            throw new BusinessException(
                    "Amount cannot be negative"
            );
        }

        log.info(
                "Agent {} searching reports. accountNumber={}, " +
                        "startDate={}, endDate={}, amount={}, paymentMode={}",
                authenticatedAgentId,
                accountNumber,
                startDate,
                endDate,
                amount,
                paymentMode
        );

        /*
         * SECURITY:
         *
         * authenticatedAgentId comes from Spring Security.
         * It is NOT supplied by the frontend.
         */
        List<Transaction> deposits =
                transactionRepository.searchDepositsForAgent(
                        TransactionType.DEPOSIT,
                        authenticatedAgentId,
                        accountNumber,
                        startDate,
                        endDate,
                        amount,
                        paymentMode
                );

        return buildFilteredReport(
                startDate,
                endDate,
                deposits
        );
    }

    private CollectionReportResponse buildReport(
            String reportType,
            LocalDate startDate,
            LocalDate displayedEndDate,
            LocalDate exclusiveEndDate) {

        Long authenticatedAgentId =
                authenticatedAgentService.getAuthenticatedAgentId();

        List<Transaction> deposits =
                transactionRepository.findDepositsInDateRangeForAgent(
                        TransactionType.DEPOSIT,
                        startDate,
                        exclusiveEndDate,
                        authenticatedAgentId
                );

        List<CollectionTransactionResponse> details =
                deposits.stream()
                        .map(this::toReportTransactionResponse)
                        .toList();

        BigDecimal totalCollection =
                deposits.stream()
                        .map(Transaction::getTransactionAmount)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
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

    private CollectionReportResponse buildFilteredReport(
            LocalDate startDate,
            LocalDate endDate,
            List<Transaction> deposits) {

        List<CollectionTransactionResponse> details =
                deposits.stream()
                        .map(this::toReportTransactionResponse)
                        .toList();

        BigDecimal totalCollection =
                deposits.stream()
                        .map(Transaction::getTransactionAmount)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        return CollectionReportResponse.builder()
                .reportType("FILTERED")
                .startDate(startDate)
                .endDate(endDate)
                .totalTransactions(deposits.size())
                .totalCollection(totalCollection)
                .transactions(details)
                .build();
    }

    private CollectionTransactionResponse toReportTransactionResponse(Transaction transaction) {
        return CollectionTransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .transactionNumber(transaction.getTransactionNumber())
                .accountId(transaction.getTransactionAccountId().getAccountId())
                .accountNumber(transaction.getTransactionAccountId().getAccountNumber())
                .customerCode(transaction.getTransactionAccountId().getCustomer().getCustomerCode())
                .transactionDate(transaction.getTransactionDate())
                .amount(transaction.getTransactionAmount())
                .paymentMode(transaction.getPaymentMode())
                .transactionType(transaction.getTransactionType())
                .transactionStatus(transaction.getTransactionStatus())
                .remarks(transaction.getRemarks())
                .build();
    }
}