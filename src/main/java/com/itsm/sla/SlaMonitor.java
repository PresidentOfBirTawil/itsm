package com.itsm.sla;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The background job: once a minute it checks every open incident against its SLA deadlines. */
@Component
@ConditionalOnProperty(name = "itsm.sla.monitor.enabled", havingValue = "true", matchIfMissing = true)
public class SlaMonitor {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitor.class);

    private final SlaService slaService;

    public SlaMonitor(SlaService slaService) {
        this.slaService = slaService;
    }

    @Scheduled(fixedRateString = "${itsm.sla.monitor.interval-ms:60000}",
            initialDelayString = "${itsm.sla.monitor.initial-delay-ms:15000}")
    public void checkOpenIncidents() {
        try {
            slaService.checkOpenIncidents(LocalDateTime.now());
        } catch (RuntimeException e) {
            // One bad run must not stop the job: log it and try again next minute.
            log.error("The SLA check failed", e);
        }
    }
}
