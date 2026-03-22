// what it does: [trackRequest, trackFromPayload, shutdown, extractIp, extractEndpoint]
package com.netwatch.service;

import com.netwatch.model.AccessLog;
import com.netwatch.repository.AccessLogRepository;
import com.netwatch.repository.IpStatRepository;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

    private final ExecutorService writeExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "netwatch-db-writer");
                t.setDaemon(true);
                return t;
            });

    private final AccessLogRepository accessLogRepo;
    private final IpStatRepository    ipStatRepo;

    public TrackingService(AccessLogRepository accessLogRepo, IpStatRepository ipStatRepo) {
        this.accessLogRepo = accessLogRepo;
        this.ipStatRepo    = ipStatRepo;
    }

    public void trackRequest(Context ctx, long startTimeMs) {
        final String        ip         = extractIp(ctx);
        final String        method     = ctx.method().name();
        final String        endpoint   = extractEndpoint(ctx);
        final String        userAgent  = ctx.userAgent();
        final String        sessionId  = ctx.sessionAttribute("id");
        final String        referer    = ctx.header("Referer");
        final LocalDateTime now        = LocalDateTime.now();
        final int           responseMs = (int) (System.currentTimeMillis() - startTimeMs);

        writeExecutor.submit(() -> {
            try {
                AccessLog entry = AccessLog.builder()
                        .ipAddress(ip)
                        .requestMethod(method)
                        .endpoint(endpoint)
                        .userAgent(userAgent)
                        .sessionId(sessionId)
                        .referer(referer)
                        .responseTimeMs(responseMs)
                        .statusCode(ctx.statusCode())
                        .accessedAt(now)
                        .build();

                accessLogRepo.insertLog(entry);
                ipStatRepo.upsertIpStat(ip, now, endpoint);

                log.debug("Tracked: {} {} -> {}", method, endpoint, ip);
            } catch (Exception e) {
                log.error("Async tracking write failed: {}", e.getMessage(), e);
            }
        });
    }

    public void trackFromPayload(String ip, String method, String endpoint,
                                  String userAgent, String sessionId) {
        final LocalDateTime now = LocalDateTime.now();
        writeExecutor.submit(() -> {
            try {
                AccessLog entry = AccessLog.builder()
                        .ipAddress(ip != null ? ip : "0.0.0.0")
                        .requestMethod(method != null ? method : "GET")
                        .endpoint(endpoint != null ? endpoint : "/")
                        .userAgent(userAgent)
                        .sessionId(sessionId)
                        .accessedAt(now)
                        .build();

                accessLogRepo.insertLog(entry);
                ipStatRepo.upsertIpStat(entry.getIpAddress(), now, entry.getEndpoint());

                log.debug("Tracked (payload): {} {} from {}", method, endpoint, ip);
            } catch (Exception e) {
                log.error("Payload tracking write failed: {}", e.getMessage(), e);
            }
        });
    }

    public void shutdown() {
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Write executor did not terminate in time; forcing shutdown.");
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("TrackingService write executor stopped.");
    }

    public String extractIp(Context ctx) {
        String xff = ctx.header("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }

        String xri = ctx.header("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }

        String cf = ctx.header("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }

        return ctx.ip();
    }

    private String extractEndpoint(Context ctx) {
        String path = ctx.path();
        return path.length() > 512 ? path.substring(0, 512) : path;
    }
}
