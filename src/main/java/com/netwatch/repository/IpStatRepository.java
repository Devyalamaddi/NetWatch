// what it does: [upsertIpStat, getTopOrigins, getAnalyticsSummary, mapRow]
package com.netwatch.repository;

import com.netwatch.config.DatabaseConfig;
import com.netwatch.model.IpStatistic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IpStatRepository {

    private static final Logger log = LoggerFactory.getLogger(IpStatRepository.class);

    private static final String SQL_UPSERT =
            "INSERT INTO ip_statistics " +
            "    (ip_address, request_count, first_seen, last_seen, most_visited_endpoint) " +
            "VALUES (?, 1, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "    request_count        = request_count + 1, " +
            "    last_seen            = VALUES(last_seen), " +
            "    most_visited_endpoint = VALUES(most_visited_endpoint)";

    private static final String SQL_TOP_ORIGINS =
            "SELECT ip_address, request_count, first_seen, last_seen, " +
            "       most_visited_endpoint, country_code, is_blocked, updated_at " +
            "FROM ip_statistics " +
            "ORDER BY request_count DESC " +
            "LIMIT ?";

    private static final String SQL_SUMMARY =
            "SELECT " +
            "  (SELECT COUNT(*) FROM network_access_logs)                        AS total_requests, " +
            "  (SELECT COUNT(*) FROM ip_statistics)                              AS unique_ips, " +
            "  (SELECT COUNT(*) FROM network_access_logs " +
            "   WHERE accessed_at >= NOW() - INTERVAL 1 HOUR)                   AS requests_last_hour, " +
            "  (SELECT COUNT(*) FROM network_access_logs " +
            "   WHERE accessed_at >= NOW() - INTERVAL 24 HOUR)                  AS requests_last_24h, " +
            "  (SELECT ROUND(COUNT(*) / 60.0, 2) FROM network_access_logs " +
            "   WHERE accessed_at >= NOW() - INTERVAL 1 HOUR)                   AS req_per_minute, " +
            "  (SELECT COUNT(*) FROM ip_statistics WHERE is_blocked = 1)         AS blocked_ips";

    private final DataSource dataSource;

    public IpStatRepository() {
        this.dataSource = DatabaseConfig.getDataSource();
    }

    public void upsertIpStat(String ipAddress, LocalDateTime now, String mostVisitedEndpoint) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPSERT)) {

            ps.setString(1, ipAddress);
            ps.setTimestamp(2, Timestamp.valueOf(now));
            ps.setTimestamp(3, Timestamp.valueOf(now));
            ps.setString(4, mostVisitedEndpoint);

            ps.executeUpdate();
            log.debug("Upserted ip_statistics for ip={}", ipAddress);

        } catch (SQLException e) {
            log.error("Failed to upsert ip_statistics for {}: {}", ipAddress, e.getMessage(), e);
        }
    }

    public List<IpStatistic> getTopOrigins(int limit) {
        List<IpStatistic> stats = new ArrayList<>(limit);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_TOP_ORIGINS)) {

            ps.setInt(1, Math.min(limit, 100));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stats.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query top origins: {}", e.getMessage(), e);
        }
        return stats;
    }

    public Map<String, Object> getAnalyticsSummary() {
        Map<String, Object> summary = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SUMMARY);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                summary.put("totalRequests",    rs.getLong("total_requests"));
                summary.put("uniqueIps",        rs.getLong("unique_ips"));
                summary.put("requestsLastHour", rs.getLong("requests_last_hour"));
                summary.put("requestsLast24h",  rs.getLong("requests_last_24h"));
                summary.put("reqPerMinute",     rs.getDouble("req_per_minute"));
                summary.put("blockedIps",       rs.getLong("blocked_ips"));
            }
        } catch (SQLException e) {
            log.error("Failed to query analytics summary: {}", e.getMessage(), e);
        }
        return summary;
    }

    private IpStatistic mapRow(ResultSet rs) throws SQLException {
        Timestamp firstSeen = rs.getTimestamp("first_seen");
        Timestamp lastSeen  = rs.getTimestamp("last_seen");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new IpStatistic(
                rs.getString("ip_address"),
                rs.getLong("request_count"),
                firstSeen != null ? firstSeen.toLocalDateTime() : null,
                lastSeen  != null ? lastSeen.toLocalDateTime()  : null,
                rs.getString("most_visited_endpoint"),
                rs.getString("country_code"),
                rs.getBoolean("is_blocked"),
                updatedAt != null ? updatedAt.toLocalDateTime() : null
        );
    }
}
