package io.github.heybadrinath.reconriver.reconciliation;

import io.github.heybadrinath.reconriver.shared.api.ApiException;
import io.github.heybadrinath.reconriver.shared.api.PageResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ResultsService {
    public static final String RESULT_PROJECTION =
            """
            SELECT r.*,COALESCE(s.external_batch_id,b.settlement_batch_id) AS settlement_batch_reference
            FROM reconciliation_result r
            LEFT JOIN settlement_batch s ON s.id=r.settlement_batch_id
            LEFT JOIN bank_settlement b ON b.id=r.bank_settlement_id
            """;

    private final JdbcClient jdbc;
    private final ReconciliationRunService runs;

    ResultsService(JdbcClient jdbc, ReconciliationRunService runs) {
        this.jdbc = jdbc;
        this.runs = runs;
    }

    public PageResponse<ResultView> list(
            UUID workspaceId,
            UUID runId,
            int page,
            int size,
            String outcome,
            String ruleCode,
            String resolutionStatus) {
        runs.get(workspaceId, runId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        StringBuilder where = new StringBuilder("r.workspace_id=:workspace AND r.run_id=:run");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("workspace", workspaceId);
        parameters.put("run", runId);
        if (outcome != null && !outcome.isBlank()) {
            try {
                Outcome.valueOf(outcome.toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_OUTCOME",
                        "The requested outcome filter is not supported.");
            }
            where.append(" AND r.outcome=:outcome");
            parameters.put("outcome", outcome.toUpperCase());
        }
        if (ruleCode != null && !ruleCode.isBlank()) {
            where.append(" AND r.rule_code=:rule");
            parameters.put("rule", ruleCode);
        }
        if (resolutionStatus != null && !resolutionStatus.isBlank()) {
            where.append(" AND r.resolution_status=:resolution");
            parameters.put("resolution", resolutionStatus.toUpperCase());
        }
        long total =
                bind(
                                jdbc.sql(
                                        "SELECT COUNT(*) FROM reconciliation_result r WHERE "
                                                + where),
                                parameters)
                        .query(Long.class)
                        .single();
        parameters.put("limit", safeSize);
        parameters.put("offset", safePage * safeSize);
        List<ResultView> items =
                bind(
                                jdbc.sql(
                                        RESULT_PROJECTION
                                                + " WHERE "
                                                + where
                                                + " ORDER BY r.created_at,r.id LIMIT :limit OFFSET :offset"),
                                parameters)
                        .query(this::resultView)
                        .list();
        return PageResponse.of(items, safePage, safeSize, total);
    }

    public SummaryView summary(UUID workspaceId, UUID runId) {
        RunView run = runs.get(workspaceId, runId);
        Totals totals =
                jdbc.sql(
                                """
                        SELECT COUNT(*) AS total,
                        COUNT(*) FILTER (WHERE outcome IN ('MATCHED','REFUND_MATCHED')) AS matched,
                        COUNT(*) FILTER (WHERE resolution_status IN ('OPEN','REOPENED')) AS open,
                        COALESCE(SUM(ABS(difference)) FILTER (WHERE resolution_status IN ('OPEN','REOPENED')),0) AS unreconciled,
                        COUNT(DISTINCT settlement_batch_id) AS batches
                        FROM reconciliation_result WHERE workspace_id=:workspace AND run_id=:run
                        """)
                        .param("workspace", workspaceId)
                        .param("run", runId)
                        .query(
                                (rs, row) ->
                                        new Totals(
                                                rs.getInt("total"),
                                                rs.getInt("matched"),
                                                rs.getInt("open"),
                                                rs.getBigDecimal("unreconciled"),
                                                rs.getInt("batches")))
                        .single();
        Map<String, Integer> outcomes = counts(runId, "outcome");
        Map<String, Integer> rules = counts(runId, "rule_code");
        double rate =
                totals.total() == 0
                        ? 0.0
                        : Math.round(totals.matched() * 10_000.0 / totals.total()) / 100.0;
        return new SummaryView(
                runId,
                totals.total(),
                totals.matched(),
                totals.total() - totals.matched(),
                rate,
                totals.open(),
                MoneyRules.normalize(totals.unreconciled()),
                totals.batches(),
                run.durationMillis(),
                outcomes,
                rules);
    }

    public byte[] report(UUID workspaceId, UUID runId) {
        runs.get(workspaceId, runId);
        List<ResultView> results =
                jdbc.sql(
                                RESULT_PROJECTION
                                        + " WHERE r.workspace_id=:workspace AND r.run_id=:run ORDER BY r.created_at,r.id")
                        .param("workspace", workspaceId)
                        .param("run", runId)
                        .query(this::resultView)
                        .list();
        StringBuilder csv =
                new StringBuilder(
                        "result_id,result_scope,work_key,settlement_batch_id,outcome,rule_code,explanation,resolution_status,internal_payment_id,processor_transaction_id,bank_entry_id,gross_amount,processor_fee,net_amount,bank_credit,difference,currency,internal_occurred_at,processor_event_at,bank_booked_at\n");
        for (ResultView result : results) {
            csv.append(csvText(result.id()))
                    .append(',')
                    .append(csvText(result.resultScope()))
                    .append(',')
                    .append(csvText(result.workKey()))
                    .append(',')
                    .append(csvText(result.settlementBatchReference()))
                    .append(',')
                    .append(csvText(result.outcome()))
                    .append(',')
                    .append(csvText(result.ruleCode()))
                    .append(',')
                    .append(csvText(result.explanation()))
                    .append(',')
                    .append(csvText(result.resolutionStatus()))
                    .append(',')
                    .append(csvText(result.internalPaymentReference()))
                    .append(',')
                    .append(csvText(result.processorTransactionReference()))
                    .append(',')
                    .append(csvText(result.bankEntryReference()))
                    .append(',')
                    .append(number(result.grossAmount()))
                    .append(',')
                    .append(number(result.processorFee()))
                    .append(',')
                    .append(number(result.netAmount()))
                    .append(',')
                    .append(number(result.bankCredit()))
                    .append(',')
                    .append(number(result.difference()))
                    .append(',')
                    .append(csvText(result.currency()))
                    .append(',')
                    .append(csvText(result.internalOccurredAt()))
                    .append(',')
                    .append(csvText(result.processorEventAt()))
                    .append(',')
                    .append(csvText(result.bankBookedAt()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public ResultView get(UUID workspaceId, UUID resultId) {
        return jdbc.sql(RESULT_PROJECTION + " WHERE r.workspace_id=:workspace AND r.id=:id")
                .param("workspace", workspaceId)
                .param("id", resultId)
                .query(this::resultView)
                .optional()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "RESULT_NOT_FOUND",
                                        "No reconciliation result exists with that ID in this workspace."));
    }

    public ResultView resultView(ResultSet rs, int row) throws SQLException {
        return new ResultView(
                rs.getObject("id", UUID.class),
                rs.getObject("run_id", UUID.class),
                ResultScope.valueOf(rs.getString("result_scope")),
                rs.getString("work_key"),
                Outcome.valueOf(rs.getString("outcome")),
                rs.getString("rule_code"),
                rs.getString("explanation"),
                rs.getString("resolution_status"),
                rs.getObject("internal_transaction_id", UUID.class),
                rs.getObject("processor_transaction_id", UUID.class),
                rs.getObject("settlement_batch_id", UUID.class),
                rs.getString("settlement_batch_reference"),
                rs.getObject("bank_settlement_id", UUID.class),
                rs.getString("internal_payment_reference"),
                rs.getString("processor_transaction_reference"),
                rs.getString("bank_entry_reference"),
                rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("processor_fee"),
                rs.getBigDecimal("net_amount"),
                rs.getBigDecimal("bank_credit"),
                rs.getBigDecimal("difference"),
                rs.getString("currency"),
                timestamp(rs, "internal_occurred_at"),
                timestamp(rs, "processor_event_at"),
                timestamp(rs, "bank_booked_at"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private Map<String, Integer> counts(UUID runId, String column) {
        if (!column.equals("outcome") && !column.equals("rule_code")) {
            throw new IllegalArgumentException("Unsupported summary column");
        }
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        jdbc.sql(
                        "SELECT "
                                + column
                                + " AS label,COUNT(*) AS total FROM reconciliation_result WHERE run_id=:run GROUP BY "
                                + column
                                + " ORDER BY total DESC,label")
                .param("run", runId)
                .query((rs, row) -> Map.entry(rs.getString("label"), rs.getInt("total")))
                .list()
                .forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        return values;
    }

    private JdbcClient.StatementSpec bind(
            JdbcClient.StatementSpec statement, Map<String, Object> parameters) {
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            statement = statement.param(parameter.getKey(), parameter.getValue());
        }
        return statement;
    }

    private Instant timestamp(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String number(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    static String csvText(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (!text.isEmpty()
                && (text.charAt(0) == '='
                        || text.charAt(0) == '+'
                        || text.charAt(0) == '-'
                        || text.charAt(0) == '@'
                        || text.charAt(0) == '\t'
                        || text.charAt(0) == '\r')) {
            text = '\'' + text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private record Totals(int total, int matched, int open, BigDecimal unreconciled, int batches) {}
}
