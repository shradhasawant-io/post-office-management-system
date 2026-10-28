package com.postoffice.agent.service;

import com.postoffice.agent.dto.account.AccountRequest;
import com.postoffice.agent.dto.account.AccountResponse;

import java.util.List;

public interface AccountService {

    AccountResponse createAccount(AccountRequest request);

    AccountResponse getAccountById(Long accountId);

    List<AccountResponse> getAllAccounts();

    AccountResponse updateAccount(Long accountId, AccountRequest request);

    List<AccountResponse> getAccountsByAgentId(Long agentId);

    List<AccountResponse> searchAccounts(String filter, String value);
}
