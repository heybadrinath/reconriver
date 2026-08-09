package io.github.heybadrinath.reconriver.reconciliation;

import io.github.heybadrinath.reconriver.shared.config.ReconRiverProperties;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
class ReconciliationBatchConfiguration {

    @Bean
    Job reconciliationJob(
            JobRepository jobRepository,
            Step prepareReconciliationWork,
            Step evaluateReconciliationWork,
            ReconciliationRunService service) {
        return new JobBuilder("reconciliationJob", jobRepository)
                .start(prepareReconciliationWork)
                .next(evaluateReconciliationWork)
                .listener(
                        new JobExecutionListener() {
                            @Override
                            public void beforeJob(JobExecution execution) {
                                service.beforeJob(runId(execution), workspaceId(execution));
                            }

                            @Override
                            public void afterJob(JobExecution execution) {
                                String failure =
                                        execution.getAllFailureExceptions().stream()
                                                .findFirst()
                                                .map(
                                                        ReconciliationBatchConfiguration
                                                                ::meaningfulFailure)
                                                .orElse(
                                                        execution
                                                                .getExitStatus()
                                                                .getExitDescription());
                                service.afterJob(
                                        runId(execution),
                                        workspaceId(execution),
                                        execution.getStatus() == BatchStatus.COMPLETED,
                                        failure);
                            }

                            private UUID runId(JobExecution execution) {
                                return UUID.fromString(
                                        execution.getJobParameters().getString("runId"));
                            }

                            private UUID workspaceId(JobExecution execution) {
                                return UUID.fromString(
                                        execution.getJobParameters().getString("workspaceId"));
                            }
                        })
                .build();
    }

    @Bean
    Step prepareReconciliationWork(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            ReconciliationRunService service) {
        return new StepBuilder("prepareReconciliationWork", repository)
                .tasklet(
                        (contribution, context) -> {
                            String run =
                                    context.getStepContext()
                                            .getJobParameters()
                                            .get("runId")
                                            .toString();
                            String workspace =
                                    context.getStepContext()
                                            .getJobParameters()
                                            .get("workspaceId")
                                            .toString();
                            service.prepareWork(UUID.fromString(run), UUID.fromString(workspace));
                            return RepeatStatus.FINISHED;
                        },
                        transactionManager)
                .build();
    }

    @Bean
    Step evaluateReconciliationWork(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            ItemReader<ReconciliationWorkItem> reconciliationWorkReader,
            ItemProcessor<ReconciliationWorkItem, ReconciliationRunService.EvaluatedWork>
                    reconciliationWorkProcessor,
            ItemWriter<ReconciliationRunService.EvaluatedWork> reconciliationWorkWriter,
            ReconRiverProperties properties) {
        return new StepBuilder("evaluateReconciliationWork", repository)
                .<ReconciliationWorkItem, ReconciliationRunService.EvaluatedWork>chunk(
                        properties.batch().chunkSize())
                .transactionManager(transactionManager)
                .reader(reconciliationWorkReader)
                .processor(reconciliationWorkProcessor)
                .writer(reconciliationWorkWriter)
                .build();
    }

    @Bean
    @StepScope
    ItemReader<ReconciliationWorkItem> reconciliationWorkReader(
            ReconciliationRunService service,
            ReconRiverProperties properties,
            @Value("#{jobParameters['runId']}") String runId) {
        return new ItemReader<>() {
            private Iterator<ReconciliationWorkItem> current =
                    List.<ReconciliationWorkItem>of().iterator();
            private String lastType = "";
            private String lastKey = "";

            @Override
            public ReconciliationWorkItem read() {
                if (!current.hasNext()) {
                    current =
                            service.nextWork(
                                            UUID.fromString(runId),
                                            lastType,
                                            lastKey,
                                            properties.batch().chunkSize())
                                    .iterator();
                }
                if (!current.hasNext()) {
                    return null;
                }
                ReconciliationWorkItem work = current.next();
                lastType = work.workType();
                lastKey = work.workKey();
                return work;
            }
        };
    }

    @Bean
    ItemProcessor<ReconciliationWorkItem, ReconciliationRunService.EvaluatedWork>
            reconciliationWorkProcessor(ReconciliationRunService service) {
        return service::evaluate;
    }

    @Bean
    @StepScope
    ItemWriter<ReconciliationRunService.EvaluatedWork> reconciliationWorkWriter(
            ReconciliationRunService service,
            ControlledFailureService controlledFailure,
            @Value("#{jobParameters['runId']}") String runId,
            @Value("#{jobParameters['controlledFailureAfterChunks']}") Long failAfterChunks) {
        return new ItemWriter<>() {
            private long chunks;

            @Override
            public void write(
                    org.springframework.batch.infrastructure.item.Chunk<
                                    ? extends ReconciliationRunService.EvaluatedWork>
                            chunk) {
                service.write(chunk.getItems());
                chunks++;
                if (failAfterChunks != null
                        && chunks >= failAfterChunks
                        && controlledFailure.triggerOnce(UUID.fromString(runId))) {
                    throw new ControlledBatchFailure(
                            "Controlled recovery scenario failed after chunk "
                                    + chunks
                                    + ". Restart the same run to resume safely.");
                }
            }
        };
    }

    static final class ControlledBatchFailure extends RuntimeException {
        ControlledBatchFailure(String message) {
            super(message);
        }
    }

    private static String meaningfulFailure(Throwable failure) {
        Throwable meaningful = failure;
        for (Throwable candidate = failure; candidate != null; candidate = candidate.getCause()) {
            if (candidate.getMessage() != null && !candidate.getMessage().isBlank()) {
                meaningful = candidate;
            }
        }
        return meaningful.getMessage();
    }
}
