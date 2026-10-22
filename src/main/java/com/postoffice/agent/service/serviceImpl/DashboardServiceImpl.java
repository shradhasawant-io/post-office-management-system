package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.dto.dashboard.DashboardResponse;
import com.postoffice.agent.enums.TransactionType;
import com.postoffice.agent.repository.AccountRepository;
import com.postoffice.agent.repository.CustomerRepository;
import com.postoffice.agent.repository.TransactionRepository;
import com.postoffice.agent.service.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public DashboardServiceImpl(CustomerRepository customerRepository, AccountRepository accountRepository, TransactionRepository transactionRepository) {

        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboardSummary() {

        LocalDate today = LocalDate.now();
        LocalDate firstDayOfMonth = today.withDayOfMonth(1);
        LocalDate firstDayOfNextMonth = firstDayOfMonth.plusMonths(1);

        long totalCustomers = customerRepository.count();
        long totalAccounts = accountRepository.count();

        BigDecimal todayDeposits = transactionRepository.sumAmountByTypeAndDate(TransactionType.DEPOSIT, today);

        BigDecimal monthlyDeposits = transactionRepository.sumAmountByTypeAndDateRange(TransactionType.DEPOSIT, firstDayOfMonth, firstDayOfNextMonth);

        return DashboardResponse.builder()
                .totalCustomers(totalCustomers)
                .totalAccounts(totalAccounts)
                .todayDeposits(todayDeposits)
                .monthlyDeposits(monthlyDeposits)
                .build();
    }
}