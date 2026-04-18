// what it does: [register, handleTrack, handleAnalytics, handleLogs, handleOrigins, parseIntParam]
package com.netwatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netwatch.model.AccessLog;
import com.netwatch.model.IpStatistic;
import com.netwatch.repository.AccessLogRepository;
import com.netwatch.repository.IpStatRepository;
import com.netwatch.service.TrackingService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final TrackingService     trackingService;
    private final AccessLogRepository accessLogRepo;
    private final IpStatRepository    ipStatRepo;
    private final ObjectMapper        mapper;

    public ApiController(TrackingService trackingService,
                         AccessLogRepository accessLogRepo,
                         IpStatRepository ipStatRepo) {
        this.trackingService = trackingService;
        this.accessLogRepo   = accessLogRepo;
        this.ipStatRepo      = ipStatRepo;

        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void register(Javalin app) {

        app.before("/api/*", ctx -> {
            ctx.header("Access-Control-Allow-Origin",  "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            ctx.header("Content-Type", "application/json");
        });

        app.options("/api/*", ctx -> ctx.status(204));

        app.post("/api/track",     this::handleTrack);
        app.get("/api/analytics",  this::handleAnalytics);
        app.get("/api/logs",       this::handleLogs);
        app.get("/api/origins",    this::handleOrigins);
        app.get("/api/health",     ctx -> ctx.status(200).result("{\"status\":\"UP\",\"service\":\"netwatch\"}"));

        log.info("API routes registered: /api/track, /api/analytics, /api/logs, /api/origins, /api/health");
    }

    private void handleTrack(Context ctx) {
        long start = System.currentTimeMillis();
        try {
            String body = ctx.body();
            if (!body.isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, String> payload = mapper.readValue(body, Map.class);

                String ip        = payload.getOrDefault("ip",        trackingService.extractIp(ctx));
                String method    = payload.getOrDefault("method",    ctx.method().name());
                String endpoint  = payload.getOrDefault("endpoint",  ctx.path());
                String userAgent = payload.getOrDefault("userAgent", ctx.userAgent());
                String sessionId = payload.getOrDefault("sessionId", null);

                trackingService.trackFromPayload(ip, method, endpoint, userAgent, sessionId);
            } else {
                trackingService.trackRequest(ctx, start);
            }
            ctx.status(204);
        } catch (Exception e) {
            log.warn("Error processing /api/track: {}", e.getMessage());
            ctx.status(400).result("{\"error\":\"Invalid request payload\"}");
        }
    }

    private void handleAnalytics(Context ctx) {
        try {
            Map<String, Object> response = new HashMap<>();

            Map<String, Object> summary = ipStatRepo.getAnalyticsSummary();
            response.put("summary", summary);

            List<Object[]> hourlyTraffic = accessLogRepo.getHourlyTraffic(24);
            response.put("hourlyTraffic", hourlyTraffic);

            List<Object[]> topEndpoints = accessLogRepo.getTopEndpoints(8);
            response.put("topEndpoints", topEndpoints);

            List<IpStatistic> topOrigins = ipStatRepo.getTopOrigins(10);
            response.put("topOrigins", topOrigins);

            List<AccessLog> recentLogs = accessLogRepo.getRecentLogs(20);
            response.put("recentLogs", recentLogs);

            ctx.status(200).result(mapper.writeValueAsString(response));
        } catch (Exception e) {
            log.error("Error building analytics response: {}", e.getMessage(), e);
            ctx.status(500).result("{\"error\":\"Failed to load analytics\"}");
        }
    }

    private void handleLogs(Context ctx) {
        try {
            int limit = parseIntParam(ctx.queryParam("limit"), 50);
            List<AccessLog> logs = accessLogRepo.getRecentLogs(limit);
            ctx.status(200).result(mapper.writeValueAsString(logs));
        } catch (Exception e) {
            log.error("Error fetching logs: {}", e.getMessage(), e);
            ctx.status(500).result("{\"error\":\"Failed to fetch logs\"}");
        }
    }

    private void handleOrigins(Context ctx) {
        try {
            int limit = parseIntParam(ctx.queryParam("limit"), 10);
            List<IpStatistic> origins = ipStatRepo.getTopOrigins(limit);
            ctx.status(200).result(mapper.writeValueAsString(origins));
        } catch (Exception e) {
            log.error("Error fetching origins: {}", e.getMessage(), e);
            ctx.status(500).result("{\"error\":\"Failed to fetch origins\"}");
        }
    }

    private int parseIntParam(String param, int defaultValue) {
        if (param == null || param.isBlank()) return defaultValue;
        try {
            return Math.max(1, Math.min(Integer.parseInt(param), 500));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
