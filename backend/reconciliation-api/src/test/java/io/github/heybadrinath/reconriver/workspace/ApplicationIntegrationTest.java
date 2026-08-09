package io.github.heybadrinath.reconriver.workspace;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.heybadrinath.reconriver.shared.config.BatchStartupRecovery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(ApplicationIntegrationTest.AsyncProbeConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=local",
            "reconriver.batch.chunk-size=500",
            "reconriver.security.requests-per-minute=100000",
            "reconriver.security.session-creations-per-minute=1000",
            "reconriver.security.uploads-per-hour=1000",
            "reconriver.security.scenario-loads-per-hour=1000",
            "reconriver.cleanup.cron=0 0 0 1 1 *",
            "reconriver.security.token-hash-secret=integration-test-secret-with-more-than-thirty-two-characters",
            "spring.datasource.hikari.maximum-pool-size=3",
            "spring.datasource.hikari.minimum-idle=0",
            "management.endpoints.web.exposure.include=health,info",
            "logging.level.root=WARN",
            "logging.level.org.springframework.batch=WARN"
        })
class ApplicationIntegrationTest {

    private static final List<String> SCENARIOS =
            List.of("clean-settlement", "mixed-exceptions", "month-end-close", "failure-recovery");
    private static final Set<String> TERMINAL_IMPORT_STATUSES =
            Set.of("COMPLETED", "COMPLETED_WITH_ERRORS", "FAILED");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("reconriver_test")
                    .withUsername("reconriver_test")
                    .withPassword("reconriver_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired TestRestTemplate rest;

    @Autowired ObjectMapper objectMapper;

    @Autowired JdbcClient jdbc;

    @Autowired EntityManagerFactory entityManagerFactory;

    @Autowired BatchStartupRecovery startupRecovery;

    @Autowired DemoSessionService demoSessions;

    @Autowired JobRepository jobRepository;

    @Autowired ApplicationContext applicationContext;

    @Autowired AsyncProbe asyncProbe;

    @Test
    void asyncExecutionUsesBoundedApplicationTaskExecutor() throws Exception {
        AsyncTaskExecutor applicationExecutor =
                applicationContext.getBean("applicationTaskExecutor", AsyncTaskExecutor.class);
        AsyncConfigurer asyncConfigurer = applicationContext.getBean(AsyncConfigurer.class);

        assertThat(asyncConfigurer.getAsyncExecutor()).isSameAs(applicationExecutor);
        assertThat(applicationExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);

        ThreadPoolTaskExecutor boundedExecutor = (ThreadPoolTaskExecutor) applicationExecutor;
        assertThat(boundedExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(boundedExecutor.getMaxPoolSize()).isEqualTo(2);
        assertThat(boundedExecutor.getQueueCapacity()).isEqualTo(4);
        assertThat(boundedExecutor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        assertThat(asyncProbe.threadName().get(5, java.util.concurrent.TimeUnit.SECONDS))
                .startsWith("application-task-");
    }

    @Test
    @Timeout(value = 12, unit = MINUTES)
    void canonicalPacksMatchGroundTruthAndOperationalLifecycle() throws Exception {
        JsonNode catalog =
                request(HttpMethod.GET, "/api/v1/demo/scenarios", null, null, HttpStatus.OK);
        assertThat(catalog.path("items")).hasSize(4);

        UUID cleanRun = null;
        String cleanToken = null;
        for (String scenario : SCENARIOS) {
            Session session = createSession();
            JsonNode scenarioView =
                    request(
                            HttpMethod.GET,
                            "/api/v1/demo/scenarios/" + scenario,
                            null,
                            null,
                            HttpStatus.OK);
            Integer controlledFailure =
                    nullableInteger(scenarioView, "controlledFailureAfterChunks");
            if (scenario.equals("failure-recovery")) {
                assertThat(controlledFailure).isEqualTo(5);
            } else {
                assertThat(controlledFailure).isNull();
            }

            JsonNode loaded =
                    request(
                            HttpMethod.POST,
                            "/api/v1/demo/scenarios/" + scenario + "/load",
                            session.token(),
                            null,
                            HttpStatus.ACCEPTED);
            assertThat(nullableInteger(loaded, "controlledFailureAfterChunks"))
                    .isEqualTo(controlledFailure);
            List<UUID> importIds = ids(loaded.path("imports"));
            assertThat(importIds).hasSize(3);
            for (UUID importId : importIds) {
                JsonNode completedImport = awaitImport(session.token(), importId);
                assertThat(completedImport.path("status").asText())
                        .isIn("COMPLETED", "COMPLETED_WITH_ERRORS");
                assertThat(
                                completedImport.path("importedRows").asInt()
                                        + completedImport.path("quarantinedRows").asInt())
                        .isEqualTo(completedImport.path("totalRows").asInt());
            }

            if (scenario.equals("clean-settlement")) {
                JsonNode duplicateLoad =
                        request(
                                HttpMethod.POST,
                                "/api/v1/demo/scenarios/clean-settlement/load",
                                session.token(),
                                null,
                                HttpStatus.ACCEPTED);
                List<UUID> duplicateIds = ids(duplicateLoad.path("imports"));
                assertThat(duplicateIds).doesNotContainAnyElementsOf(importIds);
                for (int index = 0; index < duplicateIds.size(); index++) {
                    JsonNode duplicate = duplicateLoad.path("imports").get(index);
                    assertThat(duplicate.path("status").asText()).isEqualTo("DUPLICATE");
                    assertThat(duplicate.path("duplicateOfId").asText())
                            .isEqualTo(importIds.get(index).toString());
                    assertThat(duplicate.path("totalRows").asInt()).isZero();
                }
                long storedImports =
                        jdbc.sql("SELECT COUNT(*) FROM import_file WHERE workspace_id=:workspace")
                                .param("workspace", session.workspaceId())
                                .query(Long.class)
                                .single();
                assertThat(storedImports).isEqualTo(6);
                assertThat(
                                jdbc.sql(
                                                "SELECT COUNT(*) FROM internal_transaction WHERE workspace_id=:workspace")
                                        .param("workspace", session.workspaceId())
                                        .query(Long.class)
                                        .single())
                        .isEqualTo(100);
            }

            Map<String, Object> startBody =
                    controlledFailure == null
                            ? null
                            : Map.of("controlledFailureAfterChunks", controlledFailure);
            JsonNode started;
            if (scenario.equals("month-end-close")) {
                List<RawResponse> responses =
                        concurrentPosts("/api/v1/reconciliation-runs", session.token(), startBody);
                assertThat(responses)
                        .extracting(RawResponse::status)
                        .containsExactlyInAnyOrder(HttpStatus.ACCEPTED, HttpStatus.CONFLICT);
                started =
                        responses.stream()
                                .filter(response -> response.status() == HttpStatus.ACCEPTED)
                                .findFirst()
                                .orElseThrow()
                                .body();
                JsonNode conflict =
                        responses.stream()
                                .filter(response -> response.status() == HttpStatus.CONFLICT)
                                .findFirst()
                                .orElseThrow()
                                .body();
                assertThat(conflict.path("code").asText()).isEqualTo("RUN_ALREADY_ACTIVE");
            } else {
                started =
                        request(
                                HttpMethod.POST,
                                "/api/v1/reconciliation-runs",
                                session.token(),
                                startBody,
                                HttpStatus.ACCEPTED);
            }
            UUID runId = UUID.fromString(started.path("id").asText());

            if (scenario.equals("failure-recovery")) {
                JsonNode failed = awaitRun(session.token(), runId, "FAILED");
                assertThat(failed.path("processedItems").asInt()).isEqualTo(2_000);
                assertThat(failed.path("failureMessage").asText()).contains("chunk 5");
                assertThat(
                                jdbc.sql(
                                                "SELECT COUNT(*) FROM reconciliation_result WHERE run_id=:run")
                                        .param("run", runId)
                                        .query(Long.class)
                                        .single())
                        .isEqualTo(2_000);
                assertThat(
                                jdbc.sql(
                                                "SELECT controlled_failure_triggered FROM reconciliation_run WHERE id=:run")
                                        .param("run", runId)
                                        .query(Boolean.class)
                                        .single())
                        .isTrue();

                List<RawResponse> restarts =
                        concurrentPosts(
                                "/api/v1/reconciliation-runs/" + runId + "/restart",
                                session.token(),
                                null);
                assertThat(restarts)
                        .extracting(RawResponse::status)
                        .containsExactlyInAnyOrder(HttpStatus.ACCEPTED, HttpStatus.CONFLICT);
                JsonNode restarting =
                        restarts.stream()
                                .filter(response -> response.status() == HttpStatus.ACCEPTED)
                                .findFirst()
                                .orElseThrow()
                                .body();
                JsonNode rejected =
                        restarts.stream()
                                .filter(response -> response.status() == HttpStatus.CONFLICT)
                                .findFirst()
                                .orElseThrow()
                                .body();
                assertThat(restarting.path("id").asText()).isEqualTo(runId.toString());
                assertThat(rejected.path("code").asText()).isEqualTo("RUN_RESTART_IN_PROGRESS");
            }

            JsonNode completed = awaitRun(session.token(), runId, "COMPLETED");
            assertThat(completed.path("processedItems").asInt())
                    .isEqualTo(completed.path("totalWorkItems").asInt());
            assertThat(runExecutionCount(runId))
                    .isEqualTo(scenario.equals("failure-recovery") ? 2 : 1);
            assertGroundTruth(session.token(), runId, scenario);
            assertNoDuplicateOutput(runId);
            assertSummary(session.token(), runId, scenario);

            if (scenario.equals("clean-settlement")) {
                cleanRun = runId;
                cleanToken = session.token();
            }
            if (scenario.equals("mixed-exceptions")) {
                assertResolutionReopenAndAudit(session, runId);
            }
        }

        Session otherWorkspace = createSession();
        JsonNode isolated =
                request(
                        HttpMethod.GET,
                        "/api/v1/reconciliation-runs/" + cleanRun,
                        otherWorkspace.token(),
                        null,
                        HttpStatus.NOT_FOUND);
        assertThat(isolated.path("code").asText()).isEqualTo("RUN_NOT_FOUND");
        assertThat(
                        request(
                                        HttpMethod.GET,
                                        "/api/v1/reconciliation-runs/" + cleanRun,
                                        cleanToken,
                                        null,
                                        HttpStatus.OK)
                                .path("status")
                                .asText())
                .isEqualTo("COMPLETED");

        awaitDurableEvents();
        assertThat(
                        jdbc.sql(
                                        "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL")
                                .query(Long.class)
                                .single())
                .isZero();
        assertThat(
                        jdbc.sql(
                                        "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NOT NULL")
                                .query(Long.class)
                                .single())
                .isPositive();
    }

    @Test
    @Timeout(value = 5, unit = MINUTES)
    void uploadBoundaryCommitsExactlyTwentyFiveThousandRowsAndRollsBackOverflow() {
        Session session = createSession();
        byte[] boundary = internalCsv(25_000, "BOUNDARY");
        RawResponse accepted = uploadCsv(session.token(), "boundary.csv", boundary);
        assertThat(accepted.status()).isEqualTo(HttpStatus.ACCEPTED);
        UUID importId = UUID.fromString(accepted.body().path("id").asText());
        JsonNode completed = awaitImport(session.token(), importId);
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("totalRows").asInt()).isEqualTo(25_000);
        assertThat(completed.path("importedRows").asInt()).isEqualTo(25_000);

        long importsBeforeOverflow = count("import_file", session.workspaceId());
        long stagingBeforeOverflow = count("import_staging_row", session.workspaceId());
        RawResponse overflow =
                uploadCsv(session.token(), "overflow.csv", internalCsv(25_001, "OVERFLOW"));
        assertThat(overflow.status().value()).isEqualTo(413);
        assertThat(overflow.body().path("code").asText()).isEqualTo("ROW_LIMIT_EXCEEDED");
        assertThat(count("import_file", session.workspaceId())).isEqualTo(importsBeforeOverflow);
        assertThat(count("import_staging_row", session.workspaceId()))
                .isEqualTo(stagingBeforeOverflow);
    }

    @Test
    void startupRecoveryUnblocksInterruptedImportAndRunWithoutFalseDuplicateSuccess() {
        Session session = createSession();
        byte[] csv = internalCsv(1, "RECOVERY");
        UUID importId = UUID.randomUUID();
        Instant now = Instant.now();
        JobParameters staleParameters =
                new JobParametersBuilder()
                        .addString("importId", importId.toString())
                        .addString("workspaceId", session.workspaceId().toString())
                        .toJobParameters();
        JobInstance staleInstance =
                jobRepository.createJobInstance("csvImportJob", staleParameters);
        JobExecution staleExecution =
                jobRepository.createJobExecution(
                        staleInstance, staleParameters, new ExecutionContext());
        staleExecution.setStatus(BatchStatus.STARTED);
        staleExecution.setStartTime(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        jobRepository.update(staleExecution);
        jdbc.sql(
                        """
                        INSERT INTO import_file
                          (id,workspace_id,source_type,sanitized_file_name,checksum,status,total_rows,
                           job_execution_id,created_at,updated_at)
                        VALUES
                          (:id,:workspace,'INTERNAL','recovery.csv',:checksum,'RUNNING',1,:execution,:now,:now)
                        """)
                .param("id", importId)
                .param("workspace", session.workspaceId())
                .param("checksum", sha256(csv))
                .param("execution", staleExecution.getId())
                .param("now", java.sql.Timestamp.from(now))
                .update();
        jdbc.sql(
                        """
                        INSERT INTO import_staging_row
                          (id,import_file_id,workspace_id,source_type,row_number,raw_row,created_at)
                        VALUES
                          (gen_random_uuid(),:import,:workspace,'INTERNAL',2,CAST(:raw AS jsonb),:now)
                        """)
                .param("import", importId)
                .param("workspace", session.workspaceId())
                .param(
                        "raw",
                        """
                        {"internal_payment_id":"RECOVERY-INT-000001","merchant_order_id":"RECOVERY-ORDER-000001",\
                        "occurred_at":"2026-08-01T00:00:00Z","gross_amount":"100.00","currency":"USD",\
                        "payment_status":"CAPTURED","payment_method":"CARD",\
                        "synthetic_customer_reference":"RECOVERY-CUSTOMER-000001"}
                        """)
                .param("now", java.sql.Timestamp.from(now))
                .update();
        UUID runId = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO reconciliation_run (id,workspace_id,status,created_at,updated_at)
                        VALUES (:id,:workspace,'RUNNING',:now,:now)
                        """)
                .param("id", runId)
                .param("workspace", session.workspaceId())
                .param("now", java.sql.Timestamp.from(now))
                .update();

        startupRecovery.recoverInterruptedBatches();

        assertThat(
                        jdbc.sql("SELECT status FROM import_file WHERE id=:id")
                                .param("id", importId)
                                .query(String.class)
                                .single())
                .isEqualTo("FAILED");
        assertThat(jobRepository.getJobExecution(staleExecution.getId()).getStatus())
                .isEqualTo(BatchStatus.FAILED);
        assertThat(
                        jdbc.sql("SELECT status FROM reconciliation_run WHERE id=:id")
                                .param("id", runId)
                                .query(String.class)
                                .single())
                .isEqualTo("FAILED");
        RawResponse retried = uploadCsv(session.token(), "recovery.csv", csv);
        assertThat(retried.status()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(retried.body().path("id").asText()).isEqualTo(importId.toString());
        assertThat(awaitImport(session.token(), importId).path("status").asText())
                .isEqualTo("COMPLETED");
        assertThat(
                        jdbc.sql("SELECT COUNT(*) FROM import_file WHERE workspace_id=:workspace")
                                .param("workspace", session.workspaceId())
                                .query(Long.class)
                                .single())
                .isEqualTo(1);

        request(
                HttpMethod.POST,
                "/api/v1/reconciliation-runs/" + runId + "/restart",
                session.token(),
                null,
                HttpStatus.ACCEPTED);
        assertThat(awaitRun(session.token(), runId, "COMPLETED").path("id").asText())
                .isEqualTo(runId.toString());
        assertThat(
                        jdbc.sql(
                                        "SELECT COUNT(*) FROM audit_event WHERE workspace_id=:workspace AND event_type LIKE '%INTERRUPTED_BY_RESTART'")
                                .param("workspace", session.workspaceId())
                                .query(Long.class)
                                .single())
                .isEqualTo(2);
    }

    @Test
    void cleanupCascadesExpiredWorkspaceRetainsIncompleteEventsAndPrunesTerminalBatchMetadata() {
        Session session = createSession();
        Instant old = Instant.now().minus(Duration.ofHours(25));
        jdbc.sql("UPDATE demo_workspace SET expires_at=:expired WHERE id=:id")
                .param("expired", java.sql.Timestamp.from(Instant.now().minusSeconds(1)))
                .param("id", session.workspaceId())
                .update();
        jdbc.sql(
                        """
                        INSERT INTO import_file
                          (id,workspace_id,source_type,sanitized_file_name,checksum,status,created_at,updated_at)
                        VALUES
                          (gen_random_uuid(),:workspace,'BANK','cleanup.csv',:checksum,'COMPLETED',:old,:old)
                        """)
                .param("workspace", session.workspaceId())
                .param("checksum", "a".repeat(64))
                .param("old", java.sql.Timestamp.from(old))
                .update();

        UUID completedPublication = UUID.randomUUID();
        UUID incompletePublication = UUID.randomUUID();
        insertPublication(completedPublication, old, old);
        insertPublication(incompletePublication, old, null);

        long instanceId =
                jdbc.sql("SELECT nextval('batch_job_instance_seq')").query(Long.class).single();
        long executionId =
                jdbc.sql("SELECT nextval('batch_job_execution_seq')").query(Long.class).single();
        jdbc.sql(
                        """
                        INSERT INTO batch_job_instance (job_instance_id,version,job_name,job_key)
                        VALUES (:id,0,'retentionTestJob',:key)
                        """)
                .param("id", instanceId)
                .param("key", "b".repeat(32))
                .update();
        jdbc.sql(
                        """
                        INSERT INTO batch_job_execution
                          (job_execution_id,version,job_instance_id,create_time,start_time,end_time,status,
                           exit_code,exit_message,last_updated)
                        VALUES (:execution,0,:instance,:old,:old,:old,'COMPLETED','COMPLETED','',:old)
                        """)
                .param("execution", executionId)
                .param("instance", instanceId)
                .param("old", java.sql.Timestamp.from(old))
                .update();

        assertThat(demoSessions.removeExpiredWorkspaces()).isPositive();

        assertThat(
                        jdbc.sql("SELECT COUNT(*) FROM demo_workspace WHERE id=:id")
                                .param("id", session.workspaceId())
                                .query(Long.class)
                                .single())
                .isZero();
        assertThat(
                        jdbc.sql("SELECT COUNT(*) FROM import_file WHERE workspace_id=:workspace")
                                .param("workspace", session.workspaceId())
                                .query(Long.class)
                                .single())
                .isZero();
        assertThat(
                        jdbc.sql("SELECT COUNT(*) FROM event_publication WHERE id=:id")
                                .param("id", completedPublication)
                                .query(Long.class)
                                .single())
                .isZero();
        assertThat(
                        jdbc.sql("SELECT COUNT(*) FROM event_publication WHERE id=:id")
                                .param("id", incompletePublication)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
        assertThat(
                        jdbc.sql(
                                        "SELECT COUNT(*) FROM batch_job_instance WHERE job_instance_id=:id")
                                .param("id", instanceId)
                                .query(Long.class)
                                .single())
                .isZero();
        jdbc.sql("DELETE FROM event_publication WHERE id=:id")
                .param("id", incompletePublication)
                .update();
    }

    @Test
    void httpContractDocumentsSecurityAndReturnsSanitizedProblemsWithExactCorsSurface()
            throws Exception {
        JsonNode openApi = request(HttpMethod.GET, "/v3/api-docs", null, null, HttpStatus.OK);
        assertThat(openApi.path("info").path("title").asText()).isEqualTo("ReconRiver API");
        assertThat(openApi.path("info").path("version").asText()).isEqualTo("0.1.0");
        assertThat(
                        openApi.path("components")
                                .path("securitySchemes")
                                .path("bearerAuth")
                                .path("scheme")
                                .asText())
                .isEqualTo("bearer");
        assertThat(openApi.path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(
                        openApi.path("paths")
                                .path("/api/v1/demo/sessions")
                                .path("post")
                                .path("security")
                                .size())
                .isZero();
        assertThat(
                        openApi.path("paths")
                                .path("/api/v1/demo/scenarios")
                                .path("get")
                                .path("security")
                                .size())
                .isZero();
        assertThat(
                        openApi.path("paths")
                                .path("/api/v1/demo/scenarios/{slug}/load")
                                .path("post")
                                .has("security"))
                .isFalse();
        assertThat(
                        openApi.path("components")
                                .path("schemas")
                                .path("ResultView")
                                .path("properties")
                                .has("settlementBatchReference"))
                .isTrue();
        Session session = createSession();
        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.setBearerAuth(session.token());
        invalidHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> invalid =
                rest.exchange(
                        "/api/v1/reconciliation-runs",
                        HttpMethod.POST,
                        new HttpEntity<>("{broken", invalidHeaders),
                        String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody())
                .contains("INVALID_JSON")
                .doesNotContain("HttpMessageNotReadableException", "JsonParseException");

        HttpHeaders preflightHeaders = new HttpHeaders();
        preflightHeaders.setOrigin("http://localhost:3000");
        preflightHeaders.setAccessControlRequestMethod(HttpMethod.POST);
        ResponseEntity<String> preflight =
                rest.exchange(
                        "/api/v1/demo/sessions",
                        HttpMethod.OPTIONS,
                        new HttpEntity<>(preflightHeaders),
                        String.class);
        assertThat(preflight.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(preflight.getHeaders().getAccessControlAllowOrigin())
                .isEqualTo("http://localhost:3000");
        assertThat(preflight.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.POST);

        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        HttpHeaders authenticated = new HttpHeaders();
        authenticated.setBearerAuth(session.token());
        assertThat(
                        rest.exchange(
                                        "/actuator/metrics",
                                        HttpMethod.GET,
                                        new HttpEntity<>(authenticated),
                                        String.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        Path committedContract =
                Path.of(System.getProperty("user.dir"), "../../frontend/openapi/openapi.json")
                        .normalize();
        assertThat(Files.isRegularFile(committedContract)).isTrue();
        assertThat(openApi).isEqualTo(objectMapper.readTree(committedContract.toFile()));
    }

    @Test
    void workspaceRepositoryEnforcesOptimisticVersioning() {
        Session session = createSession();
        Instant expiredAt = Instant.now().minusSeconds(60);
        jdbc.sql("UPDATE demo_workspace SET expires_at=:expires WHERE id=:id")
                .param("expires", java.sql.Timestamp.from(expiredAt))
                .param("id", session.workspaceId())
                .update();

        EntityManager firstManager = entityManagerFactory.createEntityManager();
        EntityManager secondManager = entityManagerFactory.createEntityManager();
        try {
            DemoWorkspace first = firstManager.find(DemoWorkspace.class, session.workspaceId());
            DemoWorkspace stale = secondManager.find(DemoWorkspace.class, session.workspaceId());

            firstManager.getTransaction().begin();
            first.expire(Instant.now());
            firstManager.getTransaction().commit();
            assertThat(first.version()).isEqualTo(1);

            assertThatThrownBy(
                            () -> {
                                secondManager.getTransaction().begin();
                                stale.expire(Instant.now());
                                secondManager.flush();
                                secondManager.getTransaction().commit();
                            })
                    .isInstanceOfAny(OptimisticLockException.class, RollbackException.class);
        } finally {
            if (firstManager.getTransaction().isActive()) {
                firstManager.getTransaction().rollback();
            }
            if (secondManager.getTransaction().isActive()) {
                secondManager.getTransaction().rollback();
            }
            firstManager.close();
            secondManager.close();
        }
    }

    private void assertGroundTruth(String token, UUID runId, String scenario) throws Exception {
        Map<String, ExpectedResult> expected = readExpected(scenario);
        Map<String, ActualResult> actual = new HashMap<>();
        int pages = (expected.size() + 99) / 100;
        for (int page = 0; page < pages; page++) {
            JsonNode response =
                    request(
                            HttpMethod.GET,
                            "/api/v1/reconciliation-runs/"
                                    + runId
                                    + "/results?page="
                                    + page
                                    + "&size=100",
                            token,
                            null,
                            HttpStatus.OK);
            assertThat(response.path("totalElements").asLong()).isEqualTo(expected.size());
            for (JsonNode result : response.path("items")) {
                String key =
                        key(result.path("resultScope").asText(), result.path("workKey").asText());
                ActualResult previous =
                        actual.put(
                                key,
                                new ActualResult(
                                        result.path("outcome").asText(),
                                        result.path("ruleCode").asText(),
                                        decimalOrNull(result.get("difference")),
                                        result.path("explanation").asText(),
                                        textOrNull(result, "settlementBatchReference"),
                                        textOrNull(result, "internalPaymentReference"),
                                        textOrNull(result, "processorTransactionReference"),
                                        textOrNull(result, "bankEntryReference")));
                assertThat(previous).as("duplicate API identity %s", key).isNull();
            }
        }
        assertThat(actual.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());
        expected.forEach(
                (identity, expectedResult) -> {
                    ActualResult actualResult = actual.get(identity);
                    assertThat(actualResult.outcome())
                            .as(identity)
                            .isEqualTo(expectedResult.outcome());
                    assertThat(actualResult.ruleCode())
                            .as(identity)
                            .isEqualTo(expectedResult.ruleCode());
                    assertThat(actualResult.explanation())
                            .as(identity)
                            .isEqualTo(expectedResult.explanation());
                    assertThat(actualResult.settlementBatchId())
                            .as(identity)
                            .isEqualTo(expectedResult.settlementBatchId());
                    assertThat(actualResult.internalPaymentId())
                            .as(identity)
                            .isEqualTo(expectedResult.internalPaymentId());
                    assertThat(actualResult.processorTransactionId())
                            .as(identity)
                            .isEqualTo(expectedResult.processorTransactionId());
                    assertThat(actualResult.bankEntryId())
                            .as(identity)
                            .isEqualTo(expectedResult.bankEntryId());
                    if (expectedResult.difference() == null) {
                        assertThat(actualResult.difference()).as(identity).isNull();
                    } else {
                        assertThat(actualResult.difference())
                                .as(identity)
                                .isEqualByComparingTo(expectedResult.difference());
                    }
                });
    }

    private void assertSummary(String token, UUID runId, String scenario) throws Exception {
        JsonNode summary =
                request(
                        HttpMethod.GET,
                        "/api/v1/reconciliation-runs/" + runId + "/summary",
                        token,
                        null,
                        HttpStatus.OK);
        Map<String, Integer> expectedCounts = new LinkedHashMap<>();
        readExpected(scenario)
                .values()
                .forEach(result -> expectedCounts.merge(result.outcome(), 1, Integer::sum));
        Map<String, Integer> actualCounts = new LinkedHashMap<>();
        summary.path("outcomes")
                .properties()
                .forEach(entry -> actualCounts.put(entry.getKey(), entry.getValue().asInt()));
        assertThat(summary.path("totalResults").asInt()).isEqualTo(readExpected(scenario).size());
        assertThat(actualCounts).containsExactlyInAnyOrderEntriesOf(expectedCounts);
    }

    private void assertNoDuplicateOutput(UUID runId) {
        long results =
                jdbc.sql("SELECT COUNT(*) FROM reconciliation_result WHERE run_id=:run")
                        .param("run", runId)
                        .query(Long.class)
                        .single();
        long distinctIdentities =
                jdbc.sql(
                                "SELECT COUNT(DISTINCT result_scope || '|' || work_key) FROM reconciliation_result WHERE run_id=:run")
                        .param("run", runId)
                        .query(Long.class)
                        .single();
        long workItems =
                jdbc.sql("SELECT COUNT(*) FROM reconciliation_work_item WHERE run_id=:run")
                        .param("run", runId)
                        .query(Long.class)
                        .single();
        assertThat(results).isEqualTo(distinctIdentities).isEqualTo(workItems);
    }

    private void assertResolutionReopenAndAudit(Session session, UUID runId) {
        JsonNode queue =
                request(
                        HttpMethod.GET,
                        "/api/v1/exceptions?runId=" + runId + "&status=OPEN&page=0&size=1",
                        session.token(),
                        null,
                        HttpStatus.OK);
        JsonNode exception = queue.path("items").get(0);
        UUID resultId = UUID.fromString(exception.path("id").asText());
        String originalOutcome = exception.path("outcome").asText();

        JsonNode resolved =
                request(
                        HttpMethod.POST,
                        "/api/v1/exceptions/" + resultId + "/resolve",
                        session.token(),
                        Map.of("note", "Validated against the synthetic source evidence."),
                        HttpStatus.OK);
        assertThat(resolved.path("outcome").asText()).isEqualTo("MANUALLY_RESOLVED");
        assertThat(resolved.path("resolutionStatus").asText()).isEqualTo("RESOLVED");

        JsonNode reopened =
                request(
                        HttpMethod.POST,
                        "/api/v1/exceptions/" + resultId + "/reopen",
                        session.token(),
                        Map.of(
                                "note",
                                "Reopened to demonstrate the append-only investigation history."),
                        HttpStatus.OK);
        assertThat(reopened.path("outcome").asText()).isEqualTo(originalOutcome);
        assertThat(reopened.path("resolutionStatus").asText()).isEqualTo("REOPENED");

        JsonNode openQueue =
                request(
                        HttpMethod.GET,
                        "/api/v1/exceptions?runId=" + runId + "&status=OPEN&page=0&size=100",
                        session.token(),
                        null,
                        HttpStatus.OK);
        assertThat(openQueue.path("items").findValuesAsText("id")).contains(resultId.toString());
        assertThat(openQueue.path("items").toString()).contains(resultId.toString(), "REOPENED");

        JsonNode invalidStatus =
                request(
                        HttpMethod.GET,
                        "/api/v1/exceptions?status=UNKNOWN",
                        session.token(),
                        null,
                        HttpStatus.BAD_REQUEST);
        assertThat(invalidStatus.path("code").asText()).isEqualTo("INVALID_RESOLUTION_STATUS");

        JsonNode detail =
                request(
                        HttpMethod.GET,
                        "/api/v1/exceptions/" + resultId,
                        session.token(),
                        null,
                        HttpStatus.OK);
        assertThat(detail.path("resolutions")).hasSize(2);
        assertThat(detail.path("auditEvents").toString())
                .contains("EXCEPTION_RESOLVED", "EXCEPTION_REOPENED", "WORKSPACE_OPERATOR")
                .doesNotContain("DEMO_OPERATOR");

        JsonNode audit =
                request(
                        HttpMethod.GET,
                        "/api/v1/audit-events?eventType=EXCEPTION_REOPENED&aggregateType=RECONCILIATION_RESULT",
                        session.token(),
                        null,
                        HttpStatus.OK);
        assertThat(audit.path("totalElements").asLong()).isPositive();

        UUID auditId =
                jdbc.sql(
                                "SELECT id FROM audit_event WHERE aggregate_id=:result ORDER BY occurred_at LIMIT 1")
                        .param("result", resultId)
                        .query(UUID.class)
                        .single();
        assertThatThrownBy(
                        () ->
                                jdbc.sql("UPDATE audit_event SET actor_type='SYSTEM' WHERE id=:id")
                                        .param("id", auditId)
                                        .update())
                .isInstanceOf(DataAccessException.class);
    }

    private Map<String, ExpectedResult> readExpected(String scenario) throws Exception {
        ClassPathResource resource =
                new ClassPathResource(
                        "demo-scenarios/" + scenario + "/expected_reconciliation.csv");
        try (CSVParser parser =
                CSVFormat.RFC4180
                        .builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .get()
                        .parse(
                                new InputStreamReader(
                                        resource.getInputStream(), StandardCharsets.UTF_8))) {
            Map<String, ExpectedResult> results = new LinkedHashMap<>();
            for (CSVRecord row : parser) {
                String identity = key(row.get("result_scope"), row.get("work_key"));
                ExpectedResult previous =
                        results.put(
                                identity,
                                new ExpectedResult(
                                        row.get("expected_outcome"),
                                        row.get("expected_reason_code"),
                                        row.get("expected_difference").isBlank()
                                                ? null
                                                : new BigDecimal(row.get("expected_difference")),
                                        row.get("explanation"),
                                        blankToNull(row.get("settlement_batch_id")),
                                        blankToNull(row.get("internal_payment_id")),
                                        blankToNull(row.get("processor_transaction_id")),
                                        blankToNull(row.get("bank_entry_id"))));
                assertThat(previous).as("duplicate canonical identity %s", identity).isNull();
            }
            return results;
        }
    }

    private Session createSession() {
        JsonNode response =
                request(HttpMethod.POST, "/api/v1/demo/sessions", null, null, HttpStatus.CREATED);
        return new Session(
                response.path("token").asText(),
                UUID.fromString(response.path("workspaceId").asText()));
    }

    private JsonNode awaitImport(String token, UUID importId) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(3));
        JsonNode latest = null;
        while (Instant.now().isBefore(deadline)) {
            latest =
                    request(
                            HttpMethod.GET,
                            "/api/v1/imports/" + importId,
                            token,
                            null,
                            HttpStatus.OK);
            if (TERMINAL_IMPORT_STATUSES.contains(latest.path("status").asText())) {
                return latest;
            }
            pause();
        }
        throw new AssertionError("Import did not finish: " + importId + " latest=" + latest);
    }

    private JsonNode awaitRun(String token, UUID runId, String expectedStatus) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(4));
        JsonNode latest = null;
        while (Instant.now().isBefore(deadline)) {
            latest =
                    request(
                            HttpMethod.GET,
                            "/api/v1/reconciliation-runs/" + runId + "/progress",
                            token,
                            null,
                            HttpStatus.OK);
            String status = latest.path("status").asText();
            if (status.equals(expectedStatus)) {
                return request(
                        HttpMethod.GET,
                        "/api/v1/reconciliation-runs/" + runId,
                        token,
                        null,
                        HttpStatus.OK);
            }
            if ((status.equals("FAILED") || status.equals("COMPLETED"))
                    && !status.equals(expectedStatus)) {
                throw new AssertionError(
                        "Run reached "
                                + status
                                + " while waiting for "
                                + expectedStatus
                                + ": "
                                + latest);
            }
            pause();
        }
        throw new AssertionError(
                "Run did not reach " + expectedStatus + ": " + runId + " latest=" + latest);
    }

    private void awaitDurableEvents() {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            long completed =
                    jdbc.sql(
                                    "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NOT NULL")
                            .query(Long.class)
                            .single();
            long incomplete =
                    jdbc.sql("SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL")
                            .query(Long.class)
                            .single();
            if (completed > 0 && incomplete == 0) {
                return;
            }
            pause();
        }
        throw new AssertionError("Durable event publications did not complete");
    }

    private JsonNode request(
            HttpMethod method, String path, String token, Object body, HttpStatus expectedStatus) {
        RawResponse response = rawRequest(method, path, token, body);
        assertThat(response.status())
                .as("%s %s body=%s", method, path, response.body())
                .isEqualTo(expectedStatus);
        return response.body();
    }

    private RawResponse rawRequest(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        if (token != null) {
            headers.setBearerAuth(token);
        }
        String serialized = null;
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            try {
                serialized = objectMapper.writeValueAsString(body);
            } catch (Exception exception) {
                throw new AssertionError("Could not serialize test request", exception);
            }
        }
        ResponseEntity<String> response =
                rest.exchange(path, method, new HttpEntity<>(serialized, headers), String.class);
        if (response.getBody() == null || response.getBody().isBlank()) {
            return new RawResponse((HttpStatus) response.getStatusCode(), objectMapper.nullNode());
        }
        try {
            return new RawResponse(
                    (HttpStatus) response.getStatusCode(),
                    objectMapper.readTree(response.getBody()));
        } catch (Exception exception) {
            throw new AssertionError(
                    "Response was not valid JSON: " + response.getBody(), exception);
        }
    }

    private RawResponse uploadCsv(String token, String fileName, byte[] content) {
        ByteArrayResource resource =
                new ByteArrayResource(content) {
                    @Override
                    public String getFilename() {
                        return fileName;
                    }
                };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType("text/csv"));
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("file", new HttpEntity<>(resource, partHeaders));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response =
                rest.exchange(
                        "/api/v1/imports?sourceType=INTERNAL",
                        HttpMethod.POST,
                        new HttpEntity<>(multipart, headers),
                        String.class);
        try {
            return new RawResponse(
                    (HttpStatus) response.getStatusCode(),
                    response.getBody() == null
                            ? objectMapper.nullNode()
                            : objectMapper.readTree(response.getBody()));
        } catch (Exception exception) {
            throw new AssertionError(
                    "Upload response was not valid JSON: " + response.getBody(), exception);
        }
    }

    private byte[] internalCsv(int rows, String prefix) {
        StringBuilder csv = new StringBuilder(rows * 160);
        csv.append("internal_payment_id,merchant_order_id,occurred_at,gross_amount,currency,")
                .append("payment_status,payment_method,synthetic_customer_reference\n");
        for (int index = 1; index <= rows; index++) {
            String sequence = String.format("%06d", index);
            csv.append(prefix)
                    .append("-INT-")
                    .append(sequence)
                    .append(',')
                    .append(prefix)
                    .append("-ORDER-")
                    .append(sequence)
                    .append(',')
                    .append("2026-08-01T00:00:00Z,100.00,USD,CAPTURED,CARD,")
                    .append(prefix)
                    .append("-CUSTOMER-")
                    .append(sequence)
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new AssertionError("SHA-256 is unavailable", exception);
        }
    }

    private long count(String table, UUID workspaceId) {
        if (!Set.of("import_file", "import_staging_row").contains(table)) {
            throw new IllegalArgumentException("Unsupported test table");
        }
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE workspace_id=:workspace")
                .param("workspace", workspaceId)
                .query(Long.class)
                .single();
    }

    private void insertPublication(UUID id, Instant publishedAt, Instant completedAt) {
        jdbc.sql(
                        """
                        INSERT INTO event_publication
                          (id,publication_date,listener_id,serialized_event,event_type,completion_date,
                           completion_attempts,status)
                        VALUES
                          (:id,:published,'retention-test-listener','{}','RetentionTestEvent',:completed,0,'PUBLISHED')
                        """)
                .param("id", id)
                .param("published", java.sql.Timestamp.from(publishedAt))
                .param(
                        "completed",
                        completedAt == null ? null : java.sql.Timestamp.from(completedAt))
                .update();
    }

    private List<RawResponse> concurrentPosts(String path, String token, Object body) {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<RawResponse>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    return rawRequest(HttpMethod.POST, path, token, body);
                                }));
            }
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<RawResponse> responses = new ArrayList<>();
            for (Future<RawResponse> future : futures) {
                responses.add(future.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            return responses;
        } catch (Exception exception) {
            throw new AssertionError("Concurrent API requests failed", exception);
        }
    }

    private long runExecutionCount(UUID runId) {
        long count =
                jdbc.sql(
                                """
                        SELECT COUNT(*)
                        FROM batch_job_execution je
                        WHERE je.job_instance_id=(
                          SELECT current.job_instance_id
                          FROM batch_job_execution current
                          JOIN reconciliation_run r ON r.job_execution_id=current.job_execution_id
                          WHERE r.id=:run
                        )
                        """)
                        .param("run", runId)
                        .query(Long.class)
                        .single();
        if (count == 0) {
            Long current =
                    jdbc.sql("SELECT job_execution_id FROM reconciliation_run WHERE id=:run")
                            .param("run", runId)
                            .query(Long.class)
                            .optional()
                            .orElse(null);
            List<Long> executions =
                    jdbc.sql(
                                    "SELECT job_execution_id FROM batch_job_execution ORDER BY job_execution_id")
                            .query(Long.class)
                            .list();
            throw new AssertionError(
                    "Run metadata link is missing: run="
                            + runId
                            + " current="
                            + current
                            + " batchExecutions="
                            + executions);
        }
        return count;
    }

    private List<UUID> ids(JsonNode items) {
        List<UUID> ids = new ArrayList<>();
        items.forEach(item -> ids.add(UUID.fromString(item.path("id").asText())));
        return ids;
    }

    private Integer nullableInteger(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private BigDecimal decimalOrNull(JsonNode value) {
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    private String textOrNull(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String key(String scope, String workKey) {
        return scope + '|' + workKey;
    }

    private void pause() {
        try {
            Thread.sleep(75);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test polling was interrupted", exception);
        }
    }

    private record Session(String token, UUID workspaceId) {}

    private record ExpectedResult(
            String outcome,
            String ruleCode,
            BigDecimal difference,
            String explanation,
            String settlementBatchId,
            String internalPaymentId,
            String processorTransactionId,
            String bankEntryId) {}

    private record ActualResult(
            String outcome,
            String ruleCode,
            BigDecimal difference,
            String explanation,
            String settlementBatchId,
            String internalPaymentId,
            String processorTransactionId,
            String bankEntryId) {}

    private record RawResponse(HttpStatus status, JsonNode body) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class AsyncProbeConfiguration {

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }

    static class AsyncProbe {

        @Async
        public CompletableFuture<String> threadName() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}
