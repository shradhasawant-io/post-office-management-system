package com.postoffice.agent.dto.report;

import com.postoffice.agent.enums.PaymentMode;
import com.postoffice.agent.enums.TransactionType;
import com.postoffice.agent.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionTransactionResponse {

    private Long transactionId;

    private String transactionNumber;

    private Long accountId;

    private String accountNumber;

    private String customerCode;

    private LocalDate transactionDate;

    private BigDecimal amount;

    private PaymentMode paymentMode;

    private TransactionType transactionType;

    private TransactionStatus transactionStatus;

    private String remarks;
}