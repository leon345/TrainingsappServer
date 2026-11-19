package de.trainingsapp.infrastructure.aufraeumen;

import de.trainingsapp.application.aufraeumen.AufraeumService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Täglicher Quarkus-Scheduler-Job für den DSGVO-/Hygiene-Aufräumlauf (E32).
 */
@ApplicationScoped
public class TaeglicherAufraeumJob {

    private final AufraeumService aufraeumService;

    public TaeglicherAufraeumJob(AufraeumService aufraeumService) {
        this.aufraeumService = aufraeumService;
    }

    @Scheduled(
            identity = "taeglicher-aufraeumjob",
            cron = "{trainingsapp.aufraeumen.cron:0 15 3 * * ?}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    void ausfuehren() {
        aufraeumService.aufraeumen();
    }
}
