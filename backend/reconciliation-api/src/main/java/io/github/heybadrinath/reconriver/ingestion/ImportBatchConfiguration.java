package io.github.heybadrinath.reconriver.ingestion;

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
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
class ImportBatchConfiguration {

    @Bean
    Job csvImportJob(JobRepository jobRepository, Step csvImportStep, ImportService service) {
        return new JobBuilder("csvImportJob", jobRepository)
                .start(csvImportStep)
                .listener(
                        new JobExecutionListener() {
                            @Override
                            public void beforeJob(JobExecution execution) {
                                UUID importId =
                                        UUID.fromString(
                                                execution.getJobParameters().getString("importId"));
                                UUID workspaceId =
                                        UUID.fromString(
                                                execution
                                                        .getJobParameters()
                                                        .getString("workspaceId"));
                                service.beforeJob(importId, workspaceId);
                            }

                            @Override
                            public void afterJob(JobExecution execution) {
                                UUID importId =
                                        UUID.fromString(
                                                execution.getJobParameters().getString("importId"));
                                UUID workspaceId =
                                        UUID.fromString(
                                                execution
                                                        .getJobParameters()
                                                        .getString("workspaceId"));
                                String failure =
                                        execution.getAllFailureExceptions().stream()
                                                .findFirst()
                                                .map(Throwable::getMessage)
                                                .orElse(
                                                        execution
                                                                .getExitStatus()
                                                                .getExitDescription());
                                service.afterJob(
                                        importId,
                                        workspaceId,
                                        execution.getStatus() == BatchStatus.COMPLETED,
                                        failure);
                            }
                        })
                .build();
    }

    @Bean
    Step csvImportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<StagingRow> importStagingReader,
            ItemWriter<StagingRow> importStagingWriter,
            ReconRiverProperties properties) {
        return new StepBuilder("normalizeCsvRows", jobRepository)
                .<StagingRow, StagingRow>chunk(properties.batch().chunkSize())
                .transactionManager(transactionManager)
                .reader(importStagingReader)
                .writer(importStagingWriter)
                .build();
    }

    @Bean
    @StepScope
    ItemReader<StagingRow> importStagingReader(
            ImportService service,
            ReconRiverProperties properties,
            @Value("#{jobParameters['importId']}") String importId) {
        return new ItemReader<>() {
            private Iterator<StagingRow> current = List.<StagingRow>of().iterator();
            private int lastRowNumber;

            @Override
            public StagingRow read() {
                if (!current.hasNext()) {
                    current =
                            service.nextRows(
                                            UUID.fromString(importId),
                                            lastRowNumber,
                                            properties.batch().chunkSize())
                                    .iterator();
                }
                if (!current.hasNext()) {
                    return null;
                }
                StagingRow row = current.next();
                lastRowNumber = row.rowNumber();
                return row;
            }
        };
    }

    @Bean
    ItemWriter<StagingRow> importStagingWriter(ImportService service) {
        return chunk -> service.processRows(chunk.getItems());
    }
}
