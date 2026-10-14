package com.runtimeerror.sentinel.decision;

import com.runtimeerror.sentinel.engine.DecisionEngine;
import com.runtimeerror.sentinel.engine.FeatureVector;
import com.runtimeerror.sentinel.engine.Outcome;
import com.runtimeerror.sentinel.engine.RuleSet;
import com.runtimeerror.sentinel.engine.Verdict;
import com.runtimeerror.sentinel.features.FeatureAssembler;
import com.runtimeerror.sentinel.features.Features;
import com.runtimeerror.sentinel.rules.ActiveRuleSet;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The decision path, in the order it happens:
 *
 *   1. idempotency: have we already decided this transaction? then return that answer
 *   2. assemble features: every Redis call happens here, and only here
 *   3. take the current rule set, once
 *   4. evaluate: pure computation, no I/O
 *   5. write the decision to the log, and only then answer
 *
 * Step 5 is the promise the whole system is built around: Sentinel never gives an
 * answer it has not recorded. If the database is down we return 503 and make no
 * decision at all, rather than answer and risk having no record of why.
 */
@Service
public class DecisionService {

    private final FeatureAssembler assembler;
    private final ActiveRuleSet ruleSets;
    private final DecisionEngine engine;
    private final DecisionLog log;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Timer latency;
    private final Map<Outcome, Counter> outcomeCounters = new EnumMap<>(Outcome.class);

    public DecisionService(FeatureAssembler assembler, ActiveRuleSet ruleSets, DecisionEngine engine,
                           DecisionLog log, Clock clock, MeterRegistry meters) {
        this.assembler = assembler;
        this.ruleSets = ruleSets;
        this.engine = engine;
        this.log = log;
        this.clock = clock;
        this.meters = meters;
        this.latency = Timer.builder("sentinel.decision.latency")
                .description("Time to decide one transaction, including the durable write")
                .publishPercentileHistogram()
                .register(meters);
        for (Outcome outcome : Outcome.values()) {
            outcomeCounters.put(outcome, Counter.builder("sentinel.decisions")
                    .description("Decisions made, by outcome")
                    .tag("outcome", outcome.name())
                    .register(meters));
        }
    }

    public StoredDecision decide(DecisionRequest request) {
        Timer.Sample sample = Timer.start(meters);
        try {
            return decideOnce(request);
        } finally {
            sample.stop(latency);
        }
    }

    public Optional<StoredDecision> find(String transactionId) {
        return log.findByTransactionId(transactionId);
    }

    private StoredDecision decideOnce(DecisionRequest request) {
        // 1. A caller that times out will retry with the same transaction id. It must get
        //    the same answer back, not a second decision.
        Optional<StoredDecision> existing = log.findByTransactionId(request.transactionId());
        if (existing.isPresent()) {
            return existing.get();
        }

        long startNanos = System.nanoTime();   // nanoTime: monotonic, only for measuring durations
        Instant now = clock.instant();

        // 2. All the slow work.
        FeatureVector features = assembler.assemble(
                request.cardId(), request.deviceId(), request.amountMinor(), now);

        // 3. Read the rule set once. A publish during this request cannot affect it.
        RuleSet ruleSet = ruleSets.current();

        // 4. Pure computation.
        Verdict verdict = engine.evaluate(ruleSet, features);

        int computeMicros = (int) Math.min(Integer.MAX_VALUE,
                TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNanos));
        boolean degraded = features.get(Features.SYS_VELOCITY_AVAILABLE) == 0;

        StoredDecision decision = new StoredDecision(
                UUID.randomUUID(),
                request.transactionId(),
                request.cardId(),
                request.deviceId(),
                request.merchantId(),
                request.amountMinor(),
                request.currency(),
                request.occurredAt(),
                now,
                verdict.outcome(),
                verdict.shadowOutcome(),
                verdict.reasonCodes(),
                verdict.ruleSetVersion(),
                degraded,
                features,
                verdict.trace(),
                computeMicros);

        // 5. Durable before we answer.
        try {
            log.insert(decision);
        } catch (DuplicateKeyException e) {
            // Two identical requests arrived at the same moment and both passed step 1.
            // The unique constraint let exactly one insert win. Return the winner's
            // decision so both callers get the same answer. The check in step 1 is only
            // a shortcut; this constraint is the real guarantee.
            return log.findByTransactionId(request.transactionId()).orElseThrow(() -> e);
        }

        outcomeCounters.get(verdict.outcome()).increment();
        return decision;
    }
}
