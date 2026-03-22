// what it does: [main, startServer, registerRoutes, shutdownHook]
package com.netwatch;

import com.netwatch.config.DatabaseConfig;
import com.netwatch.controller.ApiController;
import com.netwatch.repository.AccessLogRepository;
import com.netwatch.repository.IpStatRepository;
import com.netwatch.service.TrackingService;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));

        log.info("╔══════════════════════════════════════╗");
        log.info("║  NetWatch — IP Tracking & Analytics  ║");
        log.info("╚══════════════════════════════════════╝");
        log.info("Starting server on port {}", port);

        AccessLogRepository accessLogRepo   = new AccessLogRepository();
        IpStatRepository    ipStatRepo      = new IpStatRepository();
        TrackingService     trackingService = new TrackingService(accessLogRepo, ipStatRepo);
        ApiController       apiController   = new ApiController(trackingService, accessLogRepo, ipStatRepo);

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.http.maxRequestSize = 65_536L;
            config.showJavalinBanner = false;
            config.router.treatMultipleSlashesAsSingleSlash = true;
        });

        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled exception on {} {}: {}", ctx.method(), ctx.path(), e.getMessage(), e);
            ctx.status(500).result("{\"error\":\"Internal server error\"}");
        });

        apiController.register(app);

        app.after(ctx -> {
            log.info("{} {} -> HTTP {}", ctx.method(), ctx.path(), ctx.statusCode());
        });

        app.start(port);
        log.info("NetWatch dashboard available at: http://localhost:{}", port);
        log.info("API base: http://localhost:{}/api", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received — draining write queue...");
            trackingService.shutdown();
            DatabaseConfig.close();
            app.stop();
            log.info("NetWatch stopped cleanly.");
        }, "netwatch-shutdown"));
    }
}
