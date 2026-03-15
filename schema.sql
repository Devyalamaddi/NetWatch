-- what it does: [network_access_logs table, ip_statistics table, v_analytics_summary view, sp_get_hourly_traffic procedure, seed data]

CREATE DATABASE IF NOT EXISTS netwatch
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE netwatch;

CREATE TABLE IF NOT EXISTS network_access_logs (
    id                  BIGINT          UNSIGNED NOT NULL AUTO_INCREMENT,
    ip_address          VARCHAR(45)     NOT NULL,
    request_method      VARCHAR(10)     NOT NULL DEFAULT 'GET',
    endpoint            VARCHAR(512)    NOT NULL,
    user_agent          TEXT,
    session_id          VARCHAR(128),
    referer             VARCHAR(1024),
    country_code        CHAR(2),
    response_time_ms    INT             UNSIGNED,
    status_code         SMALLINT        UNSIGNED NOT NULL DEFAULT 200,
    bytes_sent          INT             UNSIGNED,
    accessed_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    INDEX idx_ip_address (ip_address),
    INDEX idx_accessed_at (accessed_at),
    INDEX idx_endpoint (endpoint(128)),
    INDEX idx_ip_time (ip_address, accessed_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS ip_statistics (
    ip_address              VARCHAR(45)     NOT NULL,
    request_count           BIGINT          UNSIGNED NOT NULL DEFAULT 1,
    first_seen              DATETIME(3)     NOT NULL,
    last_seen               DATETIME(3)     NOT NULL,
    most_visited_endpoint   VARCHAR(512),
    country_code            CHAR(2),
    is_blocked              TINYINT(1)      NOT NULL DEFAULT 0,
    updated_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                            ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (ip_address),
    INDEX idx_request_count (request_count DESC),
    INDEX idx_last_seen (last_seen DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE OR REPLACE VIEW v_analytics_summary AS
SELECT
    (SELECT COUNT(*)         FROM network_access_logs)                              AS total_requests,
    (SELECT COUNT(DISTINCT ip_address) FROM network_access_logs)                    AS unique_ips,
    (SELECT COUNT(*)         FROM network_access_logs
     WHERE accessed_at >= NOW() - INTERVAL 1 HOUR)                                 AS requests_last_hour,
    (SELECT COUNT(*)         FROM network_access_logs
     WHERE accessed_at >= NOW() - INTERVAL 24 HOUR)                                AS requests_last_24h,
    (SELECT ROUND(COUNT(*) / 60.0, 2)
     FROM network_access_logs
     WHERE accessed_at >= NOW() - INTERVAL 1 HOUR)                                 AS req_per_minute,
    (SELECT COUNT(*)         FROM ip_statistics WHERE is_blocked = 1)               AS blocked_ips;


DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS sp_get_hourly_traffic(IN hours_back INT)
BEGIN
    SELECT
        DATE_FORMAT(accessed_at, '%Y-%m-%d %H:00:00') AS hour_bucket,
        COUNT(*)                                        AS request_count
    FROM network_access_logs
    WHERE accessed_at >= NOW() - INTERVAL hours_back HOUR
    GROUP BY hour_bucket
    ORDER BY hour_bucket ASC;
END$$

DELIMITER ;


INSERT IGNORE INTO network_access_logs
    (ip_address, request_method, endpoint, user_agent, status_code, accessed_at)
VALUES
    ('203.0.113.10',  'GET',  '/api/users',    'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',  200, NOW() - INTERVAL 5 MINUTE),
    ('198.51.100.42', 'POST', '/api/login',    'curl/7.88.1',                                 200, NOW() - INTERVAL 4 MINUTE),
    ('192.0.2.15',    'GET',  '/api/products', 'Mozilla/5.0 (Macintosh; Intel Mac OS X)',     200, NOW() - INTERVAL 3 MINUTE),
    ('203.0.113.10',  'GET',  '/api/users',    'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',  200, NOW() - INTERVAL 2 MINUTE),
    ('45.33.32.156',  'GET',  '/api/health',   'python-requests/2.31.0',                      200, NOW() - INTERVAL 1 MINUTE),
    ('198.51.100.42', 'GET',  '/api/products', 'curl/7.88.1',                                 404, NOW()),
    ('10.0.0.1',      'DELETE','/api/admin',   'Postman/10.0',                                403, NOW());

INSERT INTO ip_statistics (ip_address, request_count, first_seen, last_seen, most_visited_endpoint)
VALUES
    ('203.0.113.10',  2, NOW() - INTERVAL 5 MINUTE, NOW() - INTERVAL 2 MINUTE, '/api/users'),
    ('198.51.100.42', 2, NOW() - INTERVAL 4 MINUTE, NOW(),                     '/api/products'),
    ('192.0.2.15',    1, NOW() - INTERVAL 3 MINUTE, NOW() - INTERVAL 3 MINUTE, '/api/products'),
    ('45.33.32.156',  1, NOW() - INTERVAL 1 MINUTE, NOW() - INTERVAL 1 MINUTE, '/api/health'),
    ('10.0.0.1',      1, NOW(),                     NOW(),                     '/api/admin')
ON DUPLICATE KEY UPDATE
    request_count = request_count + VALUES(request_count),
    last_seen     = VALUES(last_seen),
    most_visited_endpoint = VALUES(most_visited_endpoint);
