
package com.postoffice.agent.mapper;

import com.postoffice.agent.dto.account.AccountRequest;
import com.postoffice.agent.dto.account.AccountResponse;
import com.postoffice.agent.entity.Account;

public class AccountMapper {

    public static Account toEntity(AccountRequest request) {

        return Account.builder()
                .accountType(request.getAccountType())
                .accountNumber(request.getAccountNumber())
                .depositAmount(request.getDepositAmount())
                .frequency(request.getFrequency())
                .startDate(request.getStartDate())
                .build();
    }

    public static AccountResponse toResponse(Account account) {

        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .accountNumber(account.getAccountNumber())
                .customerId(account.getCustomer().getCustomerId())
                .customerName(account.getCustomer().getName())
                .customerCode(account.getCustomer().getCustomerCode())
                .accountType(account.getAccountType())
                .depositAmount(account.getDepositAmount())
                .frequency(account.getFrequency())
                .startDate(account.getStartDate())
                .endDate(account.getEndDate())
                .balance(account.getBalance())
                .accountStatus(account.getAccountStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
