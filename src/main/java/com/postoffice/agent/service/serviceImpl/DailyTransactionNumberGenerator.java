package com.postoffice.agent.service.serviceImpl;

import com.postoffice.agent.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared, atomic daily transaction-number allocator. */
@Component
public class DailyTransactionNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyMMdd");

    private final JdbcTemplate jdbcTemplate;

    public DailyTransactionNumberGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Must be called inside the enclosing business @Transactional method. */
    public String generate(LocalDate transactionDate) {
        if (transactionDate == null) {
            throw new BusinessException(
                    "Transaction date is required to generate transaction number");
        }

        jdbcTemplate.update("""
                INSERT INTO transaction_daily_sequence (sequence_date, sequence_value)
                VALUES (?, LAST_INSERT_ID(1))
                ON DUPLICATE KEY UPDATE
                    sequence_value = LAST_INSERT_ID(sequence_value + 1)
                """, transactionDate);

        Long sequence = jdbcTemplate.queryForObject(
                "SELECT LAST_INSERT_ID()", Long.class);

        if (sequence == null || sequence < 1 || sequence > 9999) {
            throw new BusinessException(
                    "Daily transaction number limit exceeded (maximum 9999 transactions per day)");
        }

        return "TXN" + transactionDate.format(DATE_FORMAT)
                + String.format(Locale.ROOT, "%04d", sequence);
    }
}
