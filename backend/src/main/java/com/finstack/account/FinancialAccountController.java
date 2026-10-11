package com.finstack.account;

import com.finstack.account.dto.AccountCreateRequest;
import com.finstack.account.dto.AccountResponse;
import com.finstack.account.dto.AccountUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class FinancialAccountController {

    private final FinancialAccountService accountService;

    public FinancialAccountController(FinancialAccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> list(@RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(accountService.list(active));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getById(@PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.getById(accountId));
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<AccountResponse> update(
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountUpdateRequest request
    ) {
        return ResponseEntity.ok(accountService.update(accountId, request));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID accountId) {
        accountService.deactivate(accountId);
        return ResponseEntity.noContent().build();
    }
}
