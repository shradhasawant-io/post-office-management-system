package com.postoffice.agent.dto.account;

import com.postoffice.agent.enums.AccountType;
import com.postoffice.agent.enums.Frequency;
import com.postoffice.agent.enums.PaymentMode;
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
public class AccountRequest {

    private String customerCode;

    // @deprecated Kept temporarily for backward compatibility with existing API clients
    // New account creation requests should send customerCode
    @Deprecated
    private Long customerId;

    private String accountNumber;

    private String idempotencyKey;

    private PaymentMode paymentMode;

    private AccountType accountType;

    private BigDecimal depositAmount;

    private Frequency frequency;

    private LocalDate startDate;
}
