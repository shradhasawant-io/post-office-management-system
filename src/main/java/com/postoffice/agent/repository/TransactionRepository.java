package com.postoffice.agent.repository;

import com.postoffice.agent.entity.Transaction;
import com.postoffice.agent.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    //Calculate the total amount for a transaction type on one specific date
    @Query("""
       SELECT COALESCE(SUM(t.transactionAmount), 0)
       FROM Transaction t
       WHERE t.transactionType = :transactionType
       AND t.transactionDate = :date
       """)
    BigDecimal sumAmountByTypeAndDate(
            @Param("transactionType") TransactionType transactionType,
            @Param("date") LocalDate date
    );

    //Calculate the total amount between two dates, useful for monthly reports
    @Query("""
       SELECT COALESCE(SUM(t.transactionAmount), 0)
       FROM Transaction t
       WHERE t.transactionType = :transactionType
       AND t.transactionDate >= :startDate
       AND t.transactionDate < :endDate
       """)
    BigDecimal sumAmountByTypeAndDateRange(
            @Param("transactionType") TransactionType transactionType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    //Retrieve all matching transactions between two dates, including their details.
    @Query("""
       SELECT t
       FROM Transaction t
       WHERE t.transactionType = :type
         AND t.transactionDate >= :startDate
         AND t.transactionDate < :endDate
       ORDER BY t.transactionDate DESC, t.createdAt DESC
       """)
    List<Transaction> findDepositsInDateRange(
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<Transaction> findByTransactionAccountId_AccountId(Long accountId);

    List<Transaction> findByTransactionAccountId_Customer_Agent_AgentId(Long agentId);
}