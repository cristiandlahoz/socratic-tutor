package com.wornux.services.training_activity;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.wornux.config.ApplicationProperties;
import com.wornux.data.entities.training_activity.SafeBrowserSession;
import com.wornux.data.entities.training_activity.SafeBrowserSessionStatus;
import com.wornux.data.repositories.training_activity.SafeBrowserSessionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SafeBrowserSessionExpiryWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(SafeBrowserSessionExpiryWorker.class);

    private final SafeBrowserSessionRepository sessionRepository;
    private final SafeBrowserModeService safeBrowserModeService;
    private final ApplicationProperties.SafeBrowser properties;
    private final AtomicInteger backlog = new AtomicInteger();
    private final Counter failureCounter;
    private final Timer pollLatency;

    public SafeBrowserSessionExpiryWorker(
            SafeBrowserSessionRepository sessionRepository,
            SafeBrowserModeService safeBrowserModeService,
            ApplicationProperties.SafeBrowser properties,
            MeterRegistry meterRegistry) {
        this.sessionRepository = sessionRepository;
        this.safeBrowserModeService = safeBrowserModeService;
        this.properties = properties;
        this.failureCounter = meterRegistry.counter("training.activity.safe-browser.expiry.failure");
        this.pollLatency = meterRegistry.timer("training.activity.safe-browser.expiry.latency");
        Gauge.builder("training.activity.safe-browser.expiry.backlog", backlog, AtomicInteger::get).register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.safe-browser.expiry-poll-ms:10000}")
    public int expireStaleSessions() {
        var sample = Timer.start();
        try {
            var now = Instant.now();
            try {
                var setupExpired = sessionRepository.findByStatusAndCreatedAtBefore(
                        SafeBrowserSessionStatus.PENDING, now.minus(properties.getSetupTimeout()));
                var heartbeatExpired = sessionRepository.findByStatusAndLastHeartbeatAtBefore(
                        SafeBrowserSessionStatus.ACTIVE, now.minus(properties.getHeartbeatTimeout()));
                backlog.set(setupExpired.size() + heartbeatExpired.size());
                if (backlog.get() > 0) {
                    LOGGER.info("event=safe_browser_expiry_backlog count={}", backlog.get());
                }
                return expire(setupExpired, SafeBrowserSessionStatus.PENDING, now.minus(properties.getSetupTimeout()), now)
                        + expire(heartbeatExpired, SafeBrowserSessionStatus.ACTIVE, now.minus(properties.getHeartbeatTimeout()), now);
            }
            catch (RuntimeException exception) {
                failureCounter.increment();
                LOGGER.error("event=safe_browser_expiry_poll_failed", exception);
                return 0;
            }
            finally {
                backlog.set(0);
            }
        }
        finally {
            sample.stop(pollLatency);
        }
    }

    private int expire(
            List<SafeBrowserSession> sessions, SafeBrowserSessionStatus expectedStatus, Instant cutoff, Instant now) {
        var expired = 0;
        for (var session : sessions) {
            try {
                if (safeBrowserModeService.expireStaleSession(
                        session.getAssignment().getId(), session.getId(), expectedStatus, cutoff, now)) {
                    expired++;
                }
            }
            catch (RuntimeException exception) {
                failureCounter.increment();
                LOGGER.error("event=safe_browser_expiry_item_failed session_id={}", session.getId(), exception);
            }
        }
        return expired;
    }
}
