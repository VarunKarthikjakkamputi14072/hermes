package com.hermes.worker.service;

import com.hermes.common.domain.IngestionJob;
import com.hermes.common.domain.IngestionStatus;
import com.hermes.common.event.IngestRequestedEvent;
import com.hermes.common.repository.IngestionJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@EntityScan("com.hermes.common.domain")
@EnableJpaRepositories("com.hermes.common.repository")
@Import({IngestionService.class, FakeEmbedder.class})
class IngestionServiceTest {

    @Autowired
    IngestionJobRepository jobRepository;
    @Autowired
    IngestionService service;

    private IngestRequestedEvent persistJob(String docId, int chunkCount) {
        IngestionJob job = new IngestionJob(UUID.randomUUID(), docId, "upload.pdf", chunkCount);
        jobRepository.save(job);
        return new IngestRequestedEvent(job.getId(), docId, "upload.pdf", chunkCount, Instant.now());
    }

    @Test
    void embedsAllChunksWhenJobIsValid() {
        IngestRequestedEvent event = persistJob("doc-1", 12);

        IngestionResult result = service.ingest(event);

        assertThat(result).isEqualTo(IngestionResult.COMPLETED);
        IngestionJob job = jobRepository.findById(event.jobId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(IngestionStatus.COMPLETED);
        assertThat(job.getProcessedChunks()).isEqualTo(12);
    }

    @Test
    void rejectsEmptyDocument() {
        IngestRequestedEvent event = persistJob("doc-empty", 0);

        IngestionResult result = service.ingest(event);

        assertThat(result).isEqualTo(IngestionResult.REJECTED_EMPTY_DOCUMENT);
        IngestionJob job = jobRepository.findById(event.jobId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(job.getFailureReason()).isEqualTo("EMPTY_DOCUMENT");
    }

    @Test
    void isIdempotentOnRedelivery() {
        IngestRequestedEvent event = persistJob("doc-2", 5);

        assertThat(service.ingest(event)).isEqualTo(IngestionResult.COMPLETED);
        // redelivery of the same message must not re-embed the document
        assertThat(service.ingest(event)).isEqualTo(IngestionResult.SKIPPED_DUPLICATE);
        assertThat(jobRepository.findById(event.jobId()).orElseThrow().getProcessedChunks())
                .isEqualTo(5);
    }

    @Test
    void retriesWhenJobNotYetVisible() {
        // event references a job the API's commit has not yet made visible
        IngestRequestedEvent event = new IngestRequestedEvent(
                UUID.randomUUID(), "doc-ghost", "upload.pdf", 3, Instant.now());

        assertThatThrownBy(() -> service.ingest(event))
                .isInstanceOf(JobNotYetVisibleException.class);
    }
}
