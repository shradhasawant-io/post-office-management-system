
package com.postoffice.agent.dto.account;

import com.postoffice.agent.enums.AccountStatus;
import com.postoffice.agent.enums.AccountType;
import com.postoffice.agent.enums.Frequency;
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
public class AccountResponse {

    private Long accountId;

    private String accountNumber;

    private Long customerId;

    private AccountType accountType;

    private BigDecimal depositAmount;

    private Frequency frequency;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal balance;

    private AccountStatus accountStatus;

    private LocalDateTime createdAt;
}