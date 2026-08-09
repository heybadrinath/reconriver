package io.github.heybadrinath.reconriver.reporting;

import static io.github.heybadrinath.reconriver.reconciliation.ResultsService.RESULT_PROJECTION;

import io.github.heybadrinath.reconriver.audit.AuditEventView;
import io.github.heybadrinath.reconriver.audit.AuditService;
import io.github.heybadrinath.reconriver.reconciliation.ResultView;
import io.github.heybadrinath.reconriver.reconciliation.ResultsService;
import io.github.heybadrinath.reconriver.shared.api.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class TraceService {
    private final JdbcClient jdbc;
    private final ResultsService results;
    private final AuditService audit;

    TraceService(JdbcClient jdbc, ResultsService results, AuditService audit) {
        this.jdbc = jdbc;
        this.results = results;
        this.audit = audit;
    }

    public TransactionTraceView trace(UUID workspaceId, UUID transactionId) {
        Optional<ResultView> selectedResult =
                jdbc.sql(RESULT_PROJECTION + " WHERE r.workspace_id=:workspace AND r.id=:id")
                        .param("workspace", workspaceId)
                        .param("id", transactionId)
                        .query(results::resultView)
                        .optional();
        Optional<TransactionTraceView.InternalNode> directInternal =
                internalById(
                        workspaceId,
                        selectedResult
                                .map(ResultView::internalTransactionId)
                                .orElse(transactionId));
        Optional<TransactionTraceView.ProcessorNode> directProcessor =
                processorById(
                        workspaceId,
                        selectedResult
                                .map(ResultView::processorTransactionId)
                                .orElse(transactionId));
        Optional<TransactionTraceView.BankNode> directBank =
                bankById(
                        workspaceId,
                        selectedResult.map(ResultView::bankSettlementId).orElse(transactionId));
        Optional<TransactionTraceView.SettlementNode> directSettlement =
                settlementById(
                        workspaceId,
                        selectedResult.map(ResultView::settlementBatchId).orElse(transactionId));

        String orderId =
                directInternal
                        .map(TransactionTraceView.InternalNode::merchantOrderId)
                        .or(
                                () ->
                                        directProcessor.map(
                                                TransactionTraceView.ProcessorNode
                                                        ::merchantOrderId))
                        .orElse(null);
        String externalBatch =
                directProcessor
                        .map(TransactionTraceView.ProcessorNode::settlementBatchId)
                        .or(() -> directBank.map(TransactionTraceView.BankNode::settlementBatchId))
                        .or(
                                () ->
                                        directSettlement.map(
                                                TransactionTraceView.SettlementNode
                                                        ::settlementBatchId))
                        .orElse(null);

        TransactionTraceView.InternalNode internal =
                directInternal.orElseGet(() -> firstInternal(workspaceId, orderId).orElse(null));
        List<TransactionTraceView.ProcessorNode> processors =
                processors(workspaceId, orderId, externalBatch);
        TransactionTraceView.ProcessorNode payment =
                directProcessor
                        .filter(node -> !node.processorEventType().equalsIgnoreCase("REFUND"))
                        .orElseGet(
                                () ->
                                        processors.stream()
                                                .filter(
                                                        node ->
                                                                !node.processorEventType()
                                                                        .equalsIgnoreCase("REFUND"))
                                                .findFirst()
                                                .orElse(null));
        TransactionTraceView.ProcessorNode refund =
                directProcessor
                        .filter(node -> node.processorEventType().equalsIgnoreCase("REFUND"))
                        .orElseGet(
                                () ->
                                        processors.stream()
                                                .filter(
                                                        node ->
                                                                node.processorEventType()
                                                                        .equalsIgnoreCase("REFUND"))
                                                .findFirst()
                                                .orElse(null));
        if (externalBatch == null && payment != null) {
            externalBatch = payment.settlementBatchId();
        }
        final String linkedBatch = externalBatch;
        TransactionTraceView.SettlementNode settlement =
                directSettlement.orElseGet(
                        () -> firstSettlement(workspaceId, linkedBatch).orElse(null));
        TransactionTraceView.BankNode bank =
                directBank.orElseGet(() -> firstBank(workspaceId, linkedBatch).orElse(null));

        if (selectedResult.isEmpty()
                && internal == null
                && payment == null
                && refund == null
                && settlement == null
                && bank == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "TRANSACTION_NOT_FOUND",
                    "No transaction or reconciliation result exists with that ID in this workspace.");
        }
        List<ResultView> decisions = decisions(workspaceId, orderId, linkedBatch, selectedResult);
        LinkedHashMap<UUID, AuditEventView> events = new LinkedHashMap<>();
        for (ResultView decision : decisions) {
            audit.forAggregate(workspaceId, decision.id())
                    .forEach(event -> events.put(event.id(), event));
        }
        return new TransactionTraceView(
                transactionId,
                internal,
                payment,
                settlement,
                bank,
                refund,
                decisions,
                new ArrayList<>(events.values()));
    }

    private Optional<TransactionTraceView.InternalNode> internalById(UUID workspaceId, UUID id) {
        if (id == null) return Optional.empty();
        return jdbc.sql(
                        "SELECT * FROM internal_transaction WHERE workspace_id=:workspace AND id=:id")
                .param("workspace", workspaceId)
                .param("id", id)
                .query(this::internal)
                .optional();
    }

    private Optional<TransactionTraceView.ProcessorNode> processorById(UUID workspaceId, UUID id) {
        if (id == null) return Optional.empty();
        return jdbc.sql(
                        "SELECT * FROM processor_transaction WHERE workspace_id=:workspace AND id=:id")
                .param("workspace", workspaceId)
                .param("id", id)
                .query(this::processor)
                .optional();
    }

    private Optional<TransactionTraceView.BankNode> bankById(UUID workspaceId, UUID id) {
        if (id == null) return Optional.empty();
        return jdbc.sql("SELECT * FROM bank_settlement WHERE workspace_id=:workspace AND id=:id")
                .param("workspace", workspaceId)
                .param("id", id)
                .query(this::bank)
                .optional();
    }

    private Optional<TransactionTraceView.SettlementNode> settlementById(
            UUID workspaceId, UUID id) {
        if (id == null) return Optional.empty();
        return jdbc.sql("SELECT * FROM settlement_batch WHERE workspace_id=:workspace AND id=:id")
                .param("workspace", workspaceId)
                .param("id", id)
                .query(this::settlement)
                .optional();
    }

    private Optional<TransactionTraceView.InternalNode> firstInternal(
            UUID workspaceId, String orderId) {
        if (orderId == null) return Optional.empty();
        return jdbc.sql(
                        "SELECT * FROM internal_transaction WHERE workspace_id=:workspace AND merchant_order_id=:orderId ORDER BY occurred_at,id LIMIT 1")
                .param("workspace", workspaceId)
                .param("orderId", orderId)
                .query(this::internal)
                .optional();
    }

    private List<TransactionTraceView.ProcessorNode> processors(
            UUID workspaceId, String orderId, String batch) {
        if (orderId != null) {
            return jdbc.sql(
                            "SELECT * FROM processor_transaction WHERE workspace_id=:workspace AND merchant_order_id=:orderId ORDER BY processor_event_time,id")
                    .param("workspace", workspaceId)
                    .param("orderId", orderId)
                    .query(this::processor)
                    .list();
        }
        if (batch != null) {
            return jdbc.sql(
                            "SELECT * FROM processor_transaction WHERE workspace_id=:workspace AND settlement_batch_id=:batch ORDER BY processor_event_time,id")
                    .param("workspace", workspaceId)
                    .param("batch", batch)
                    .query(this::processor)
                    .list();
        }
        return List.of();
    }

    private Optional<TransactionTraceView.SettlementNode> firstSettlement(
            UUID workspaceId, String batch) {
        if (batch == null) return Optional.empty();
        return jdbc.sql(
                        "SELECT * FROM settlement_batch WHERE workspace_id=:workspace AND external_batch_id=:batch ORDER BY currency,id LIMIT 1")
                .param("workspace", workspaceId)
                .param("batch", batch)
                .query(this::settlement)
                .optional();
    }

    private Optional<TransactionTraceView.BankNode> firstBank(UUID workspaceId, String batch) {
        if (batch == null) return Optional.empty();
        return jdbc.sql(
                        "SELECT * FROM bank_settlement WHERE workspace_id=:workspace AND settlement_batch_id=:batch ORDER BY booked_at,id LIMIT 1")
                .param("workspace", workspaceId)
                .param("batch", batch)
                .query(this::bank)
                .optional();
    }

    private List<ResultView> decisions(
            UUID workspaceId, String orderId, String batch, Optional<ResultView> selected) {
        if (orderId != null) {
            return jdbc.sql(
                            """
                            SELECT r.*,COALESCE(s.external_batch_id,b.settlement_batch_id) AS settlement_batch_reference
                            FROM reconciliation_result r
                            LEFT JOIN settlement_batch s ON s.id=r.settlement_batch_id
                            LEFT JOIN bank_settlement b ON b.id=r.bank_settlement_id
                            JOIN reconciliation_work_item w ON w.id=r.work_item_id
                            WHERE r.workspace_id=:workspace AND w.work_type='ORDER' AND w.work_key=:orderId ORDER BY r.created_at,r.id
                            """)
                    .param("workspace", workspaceId)
                    .param("orderId", orderId)
                    .query(results::resultView)
                    .list();
        }
        if (batch != null) {
            return jdbc.sql(
                            """
                            SELECT DISTINCT r.*,COALESCE(s.external_batch_id,b.settlement_batch_id) AS settlement_batch_reference
                            FROM reconciliation_result r
                            LEFT JOIN settlement_batch s ON s.id=r.settlement_batch_id
                            LEFT JOIN processor_transaction p ON p.id=r.processor_transaction_id
                            LEFT JOIN bank_settlement b ON b.id=r.bank_settlement_id
                            WHERE r.workspace_id=:workspace AND (s.external_batch_id=:batch OR p.settlement_batch_id=:batch OR b.settlement_batch_id=:batch)
                            ORDER BY r.created_at,r.id
                            """)
                    .param("workspace", workspaceId)
                    .param("batch", batch)
                    .query(results::resultView)
                    .list();
        }
        return selected.map(List::of).orElseGet(List::of);
    }

    private TransactionTraceView.InternalNode internal(ResultSet rs, int row) throws SQLException {
        return new TransactionTraceView.InternalNode(
                rs.getObject("id", UUID.class),
                rs.getString("internal_payment_id"),
                rs.getString("merchant_order_id"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getBigDecimal("gross_amount"),
                rs.getString("currency"),
                rs.getString("payment_status"),
                rs.getString("payment_method"),
                rs.getString("synthetic_customer_reference"));
    }

    private TransactionTraceView.ProcessorNode processor(ResultSet rs, int row)
            throws SQLException {
        return new TransactionTraceView.ProcessorNode(
                rs.getObject("id", UUID.class),
                rs.getString("processor_transaction_id"),
                rs.getString("merchant_order_id"),
                rs.getString("processor_event_type"),
                rs.getTimestamp("processor_event_time").toInstant(),
                rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("fee_amount"),
                rs.getBigDecimal("net_amount"),
                rs.getString("currency"),
                rs.getString("settlement_batch_id"),
                rs.getString("processor_status"));
    }

    private TransactionTraceView.SettlementNode settlement(ResultSet rs, int row)
            throws SQLException {
        return new TransactionTraceView.SettlementNode(
                rs.getObject("id", UUID.class),
                rs.getString("external_batch_id"),
                rs.getString("currency"),
                rs.getBigDecimal("expected_credit"),
                rs.getTimestamp("first_event_at").toInstant(),
                rs.getTimestamp("last_event_at").toInstant(),
                rs.getInt("transaction_count"));
    }

    private TransactionTraceView.BankNode bank(ResultSet rs, int row) throws SQLException {
        return new TransactionTraceView.BankNode(
                rs.getObject("id", UUID.class),
                rs.getString("bank_entry_id"),
                rs.getString("settlement_batch_id"),
                rs.getTimestamp("booked_at").toInstant(),
                rs.getBigDecimal("credited_amount"),
                rs.getString("currency"),
                rs.getString("bank_reference"),
                rs.getString("description"));
    }
}
