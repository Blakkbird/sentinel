package com.runtimeerror.sentinel.api;

import com.runtimeerror.sentinel.decision.DecisionRequest;
import com.runtimeerror.sentinel.decision.DecisionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HTTP front door. It translates between JSON and our Java types and does nothing
 * else. All real logic lives in DecisionService, which keeps this class trivial to read.
 *
 *   POST /v1/decisions                  decide a transaction
 *   GET  /v1/decisions/{transactionId}  explain a past decision
 *
 * The "v1" in the path is API versioning: if the contract ever has to change in an
 * incompatible way, a v2 can run alongside v1 while callers migrate.
 */
@RestController
@RequestMapping("/v1/decisions")
public class DecisionController {

    private final DecisionService decisions;

    public DecisionController(DecisionService decisions) {
        this.decisions = decisions;
    }

    @PostMapping
    public DecisionResponse decide(@Valid @RequestBody DecisionRequest request) {
        return DecisionResponse.from(decisions.decide(request));
    }

    @GetMapping("/{transactionId}")
    public DecisionDetail explain(@PathVariable String transactionId) {
        return decisions.find(transactionId)
                .map(DecisionDetail::from)
                .orElseThrow(() -> new DecisionNotFoundException(transactionId));
    }
}
