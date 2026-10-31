package com.postoffice.agent.repository;

import com.postoffice.agent.entity.Transaction;
import com.postoffice.agent.enums.PaymentMode;
import com.postoffice.agent.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    long countByTransactionAccountId_AccountIdAndTransactionType(
            Long accountId,
            TransactionType transactionType
    );

    long countByTransactionAccountId_AccountIdAndTransactionTypeAndTransactionIdLessThanEqual(
            Long accountId,
            TransactionType transactionType,
            Long transactionId
    );

    List<Transaction> findByTransactionAccountId_AccountIdOrderByTransactionIdAsc(
            Long accountId
    );
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

    @Query("""
    SELECT t
    FROM Transaction t
    JOIN t.transactionAccountId a
    JOIN a.customer c
    JOIN c.agent agent
    WHERE t.transactionType = :type
      AND t.transactionDate >= :startDate
      AND t.transactionDate < :endDate
      AND agent.agentId = :agentId
    ORDER BY t.transactionDate DESC, t.createdAt DESC
    """)
    List<Transaction> findDepositsInDateRangeForAgent(
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("agentId") Long agentId
    );

    @Query("""
    SELECT t
    FROM Transaction t
    JOIN t.transactionAccountId a
    JOIN a.customer c
    WHERE t.transactionType = :type
      AND c.agent.agentId = :agentId
      AND (
            :accountNumber IS NULL
            OR LOWER(a.accountNumber) LIKE LOWER(CONCAT('%', :accountNumber, '%'))
          )
      AND (
            :startDate IS NULL
            OR t.transactionDate >= :startDate
          )
      AND (
            :endDate IS NULL
            OR t.transactionDate <= :endDate
          )
      AND (
            :amount IS NULL
            OR t.transactionAmount = :amount
          )
      AND (
            :paymentMode IS NULL
            OR t.paymentMode = :paymentMode
          )
    ORDER BY t.transactionDate DESC, t.createdAt DESC
    """)
    List<Transaction> searchDepositsForAgent(
            @Param("type") TransactionType type,
            @Param("agentId") Long agentId,
            @Param("accountNumber") String accountNumber,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("amount") BigDecimal amount,
            @Param("paymentMode") PaymentMode paymentMode
    );

    List<Transaction> findByTransactionAccountId_AccountId(Long accountId);

    /**
     * Loads historical transactions for all accounts being deactivated.
     * Rows are updated in place; they are never deleted.
     */
    List<Transaction> findByTransactionAccountId_AccountIdIn(List<Long> accountIds);

    List<Transaction> findByTransactionAccountId_Customer_Agent_AgentId(Long agentId);
}