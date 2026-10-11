package com.finstack.fraud;

import com.finstack.fraud.dto.FraudAssessmentResponse;
import com.finstack.fraud.dto.FraudSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud")
public class FraudController {
    private final FraudQueryService fraudQueries;

    public FraudController(FraudQueryService fraudQueries) {
        this.fraudQueries = fraudQueries;
    }

    @GetMapping("/transactions/{transactionId}")
    public FraudAssessmentResponse byTransaction(@PathVariable UUID transactionId) {
        return fraudQueries.byTransaction(transactionId);
    }

    @GetMapping("/assessments")
    public Page<FraudAssessmentResponse> list(@PageableDefault(size = 20, sort = "evaluatedAt") Pageable pageable) {
        return fraudQueries.list(pageable);
    }

    @GetMapping("/summary")
    public FraudSummaryResponse summary() {
        return fraudQueries.summary();
    }
}
