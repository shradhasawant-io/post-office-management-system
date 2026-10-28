package com.postoffice.agent.controller;

import com.postoffice.agent.dto.account.AccountRequest;
import com.postoffice.agent.dto.account.AccountResponse;
import com.postoffice.agent.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // CREATE ACCOUNT
    @PostMapping
    @PreAuthorize("hasRole('AGENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@RequestBody AccountRequest request) {
        return accountService.createAccount(request);
    }

    // GET BY ACCOUNT ID
    @GetMapping("/{accountId}")
    @PreAuthorize("hasRole('AGENT')")
    public AccountResponse getAccountById(@PathVariable Long accountId) {
        return accountService.getAccountById(accountId);
    }

    // GET ALL ACCOUNTS
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AccountResponse> getAllAccounts() {
        return accountService.getAllAccounts();
    }


    // UPDATE ACCOUNT
    @PutMapping("/{accountId}")
    @PreAuthorize("hasRole('AGENT')")
    public AccountResponse updateAccount(@PathVariable Long accountId, @Valid @RequestBody AccountRequest request) {
        return accountService.updateAccount(accountId, request);
    }

    // GET ACCOUNTS BY AGENT
    @GetMapping("/agents/{agentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AccountResponse>> getAccountsByAgent(@PathVariable Long agentId) {
        return ResponseEntity.ok(accountService.getAccountsByAgentId(agentId));
    }

    // Search Accounts
    @GetMapping("/search")
    @PreAuthorize("hasRole('AGENT')")
    public List<AccountResponse> searchAccounts(
            @RequestParam String filter,
            @RequestParam String value) {

        return accountService.searchAccounts(
                filter,
                value
        );
    }
}