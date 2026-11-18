package com.quell.sla;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches on Spring's scheduler, which runs the SLA check once a minute.
 * The tests switch it off (quell.sla.monitor.enabled=false) and call the
 * check themselves, so that they decide what time it is.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "quell.sla.monitor.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
