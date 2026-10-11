package com.finstack.transaction;

import com.finstack.transaction.dto.TransactionCreateRequest;
import com.finstack.transaction.dto.TransactionResponse;
import com.finstack.transaction.dto.TransactionSearchCriteria;
import com.finstack.transaction.dto.TransactionUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final IdempotentTransactionCreationService idempotentCreation;

    public TransactionController(TransactionService transactionService, IdempotentTransactionCreationService idempotentCreation) {
        this.transactionService = transactionService;
        this.idempotentCreation = idempotentCreation;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionCreateRequest request,
                                                      @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                key == null ? transactionService.create(request) : idempotentCreation.create(request, key));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> search(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "transactionTime", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        TransactionSearchCriteria criteria = new TransactionSearchCriteria(
                accountId, categoryId, type, status, from, to, search
        );
        return ResponseEntity.ok(transactionService.search(criteria, pageable));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getById(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(transactionService.getById(transactionId));
    }

    @PutMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> update(
            @PathVariable UUID transactionId,
            @Valid @RequestBody TransactionUpdateRequest request
    ) {
        return ResponseEntity.ok(transactionService.update(transactionId, request));
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID transactionId) {
        transactionService.cancel(transactionId);
        return ResponseEntity.noContent().build();
    }
}
