package com.runtimeerror.sentinel.console;

import com.runtimeerror.sentinel.engine.RuleMode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * The numbers behind the dashboard page. Plain SQL, read-only.
 *
 * Three statements per request, whatever the window. The database is remote (Neon), so each
 * statement costs a network round trip of several milliseconds; counting in SQL and sending
 * back a handful of rows is far cheaper than fetching decisions and counting them here.
 *
 * Every statement limits decided_at with the same two parameters, :from and :to. Passing both
 * as plain values (rather than writing "now() - interval ..." in the SQL) lets Postgres go
 * straight to that slice through the index on decided_at, and it means "now" is the
 * application's clock, which tests can freeze.
 *
 * The three statements run one after another, not in one transaction, so under live traffic
 * the totals can be a decision or two ahead of the chart. The page refreshes every few seconds.
 */
@Repository
public class DashboardQueries {

    private static final int TOP_RULES = 8;

    // One row, always: an aggregate without GROUP BY returns a row even when nothing matched.
    // The counts are then 0 and the percentiles are NULL.
    // count(*) filter (where ...) counts only the rows passing the test, so one pass over the
    // window gives every total. percentile_cont(0.95) is the value 95% of rows are at or below,
    // interpolated between the two nearest rows when it falls between them.
    private static final String TOTALS = """
            select count(*)                                                   as decisions,
                   count(*) filter (where outcome = 'APPROVE')                as approve,
                   count(*) filter (where outcome = 'STEP_UP')                as step_up,
                   count(*) filter (where outcome = 'DECLINE')                as decline,
                   count(*) filter (where degraded)                           as degraded,
                   count(*) filter (where outcome <> shadow_outcome)          as shadow_changes,
                   percentile_cont(0.5)  within group (order by compute_micros) as p50,
                   percentile_cont(0.95) within group (order by compute_micros) as p95
            from decision_log
            where decided_at >= :from and decided_at < :to
            """;

    // "counted" puts each decision in its bucket (date_bin rounds decided_at down to a multiple
    // of the bucket length, measured from :from) and counts per bucket. That alone would skip
    // buckets in which nothing happened, and the chart would close the gap as if no time had
    // passed. So generate_series lists every bucket start in the window, and the left join keeps
    // each one, with zeros where "counted" has no row.
    private static final String SERIES = """
            with counted as (
                select date_bin(make_interval(secs => :bucketSeconds), decided_at, cast(:from as timestamptz)) as starts_at,
                       count(*) filter (where outcome = 'APPROVE') as approve,
                       count(*) filter (where outcome = 'STEP_UP') as step_up,
                       count(*) filter (where outcome = 'DECLINE') as decline
                from decision_log
                where decided_at >= :from and decided_at < :to
                group by 1
            )
            select bucket.starts_at,
                   coalesce(counted.approve, 0) as approve,
                   coalesce(counted.step_up, 0) as step_up,
                   coalesce(counted.decline, 0) as decline
            from generate_series(cast(:from as timestamptz), cast(:lastBucket as timestamptz),
                                 make_interval(secs => :bucketSeconds)) as bucket(starts_at)
            left join counted on counted.starts_at = bucket.starts_at
            order by bucket.starts_at
            """;

    // The trace column is a JSON array with one entry per rule that was evaluated.
    // jsonb_array_elements turns that array into rows, one per entry, so ordinary GROUP BY can
    // count them. Shadow rules are in the trace like any other, so they are counted too, and the
    // mode says which is which. The rule code breaks ties, so equal counts keep a steady order.
    private static final String TOP_RULES_FIRED = """
            select entry ->> 'ruleCode' as rule_code,
                   entry ->> 'mode'     as mode,
                   count(*)             as fired
            from decision_log
            cross join lateral jsonb_array_elements(trace) as evaluated(entry)
            where decided_at >= :from and decided_at < :to
              and entry ->> 'result' = 'FIRED'
            group by 1, 2
            order by fired desc, rule_code, mode
            limit :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public DashboardQueries(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public DashboardView summarise(DashboardWindow window) {
        Instant now = clock.instant();
        Instant from = window.from(now);
        Instant to = window.to(now);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", utc(from))
                .addValue("to", utc(to))
                .addValue("lastBucket", utc(to.minus(window.bucket())))
                .addValue("bucketSeconds", window.bucket().toSeconds())
                .addValue("limit", TOP_RULES);

        Summary summary = jdbc.queryForObject(TOTALS, params, DashboardQueries::mapSummary);
        List<DashboardView.Point> series = jdbc.query(SERIES, params, DashboardQueries::mapPoint);
        List<DashboardView.TopRule> topRules = jdbc.query(TOP_RULES_FIRED, params, DashboardQueries::mapTopRule);

        return new DashboardView(window.label(), now, window.bucket().toSeconds(),
                summary.totals(), summary.computeMicros(), series, topRules);
    }

    /** The totals and the percentiles arrive in the same row; this carries both out of the row mapper. */
    private record Summary(DashboardView.Totals totals, DashboardView.ComputeMicros computeMicros) {
    }

    private static Summary mapSummary(ResultSet rs, int rowNumber) throws SQLException {
        DashboardView.Totals totals = new DashboardView.Totals(
                rs.getLong("decisions"),
                rs.getLong("approve"),
                rs.getLong("step_up"),
                rs.getLong("decline"),
                rs.getLong("degraded"),
                rs.getLong("shadow_changes"));
        // getObject, not getDouble: getDouble would turn the NULL of an empty window into 0.0.
        DashboardView.ComputeMicros computeMicros = new DashboardView.ComputeMicros(
                rs.getObject("p50", Double.class),
                rs.getObject("p95", Double.class));
        return new Summary(totals, computeMicros);
    }

    private static DashboardView.Point mapPoint(ResultSet rs, int rowNumber) throws SQLException {
        return new DashboardView.Point(
                rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                rs.getLong("approve"),
                rs.getLong("step_up"),
                rs.getLong("decline"));
    }

    private static DashboardView.TopRule mapTopRule(ResultSet rs, int rowNumber) throws SQLException {
        return new DashboardView.TopRule(
                rs.getString("rule_code"),
                RuleMode.valueOf(rs.getString("mode")),
                rs.getLong("fired"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
