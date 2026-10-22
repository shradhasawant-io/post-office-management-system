
package com.postoffice.agent.dto.transaction;

import com.postoffice.agent.enums.PaymentMode;
import com.postoffice.agent.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private Long transactionId;

    private Long transactionAccountId;

    private LocalDate transactionDate;

    private TransactionType transactionType;

    private BigDecimal transactionAmount;

    private PaymentMode paymentMode;

    private String remarks;

    private BigDecimal balance;

    private LocalDateTime createdAt;
}
