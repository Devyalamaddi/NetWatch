// what it does: [insertLog, getRecentLogs, getHourlyTraffic, getTopEndpoints, mapRow, truncate, setNullableInt, getIntOrNull]
package com.netwatch.repository;

import com.netwatch.config.DatabaseConfig;
import com.netwatch.model.AccessLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccessLogRepository {

    private static final Logger log = LoggerFactory.getLogger(AccessLogRepository.class);

    private static final String SQL_INSERT =
            "INSERT INTO network_access_logs " +
            "(ip_address, request_method, endpoint, user_agent, session_id, referer, " +
            " country_code, response_time_ms, status_code, bytes_sent, accessed_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_RECENT =
            "SELECT id, ip_address, request_method, endpoint, user_agent, session_id, " +
            "       referer, country_code, response_time_ms, status_code, bytes_sent, accessed_at " +
            "FROM network_access_logs " +
            "ORDER BY accessed_at DESC " +
            "LIMIT ?";

    private static final String SQL_HOURLY_TRAFFIC =
            "SELECT DATE_FORMAT(accessed_at, '%Y-%m-%d %H:00:00') AS hour_bucket, " +
            "       COUNT(*) AS request_count " +
            "FROM network_access_logs " +
            "WHERE accessed_at >= NOW() - INTERVAL ? HOUR " +
            "GROUP BY hour_bucket " +
            "ORDER BY hour_bucket ASC";

    private static final String SQL_TOP_ENDPOINTS =
            "SELECT endpoint, COUNT(*) AS hit_count " +
            "FROM network_access_logs " +
            "GROUP BY endpoint " +
            "ORDER BY hit_count DESC " +
            "LIMIT ?";

    private final DataSource dataSource;

    public AccessLogRepository() {
        this.dataSource = DatabaseConfig.getDataSource();
    }

    public long insertLog(AccessLog entry) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, truncate(entry.getIpAddress(), 45));
            ps.setString(2, truncate(entry.getRequestMethod(), 10));
            ps.setString(3, truncate(entry.getEndpoint(), 512));
            ps.setString(4, entry.getUserAgent());
            ps.setString(5, truncate(entry.getSessionId(), 128));
            ps.setString(6, truncate(entry.getReferer(), 1024));
            ps.setString(7, truncate(entry.getCountryCode(), 2));
            setNullableInt(ps, 8, entry.getResponseTimeMs());
            ps.setInt(9, entry.getStatusCode());
            setNullableInt(ps, 10, entry.getBytesSent());
            ps.setTimestamp(11, Timestamp.valueOf(
                    entry.getAccessedAt() != null ? entry.getAccessedAt() : LocalDateTime.now()));

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long generatedId = keys.getLong(1);
                    log.debug("Inserted access log id={} ip={}", generatedId, entry.getIpAddress());
                    return generatedId;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to insert access log for IP {}: {}", entry.getIpAddress(), e.getMessage(), e);
        }
        return -1L;
    }

    public List<AccessLog> getRecentLogs(int limit) {
        List<AccessLog> logs = new ArrayList<>(limit);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RECENT)) {

            ps.setInt(1, Math.min(limit, 500));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query recent logs: {}", e.getMessage(), e);
        }
        return logs;
    }

    public List<Object[]> getHourlyTraffic(int hoursBack) {
        List<Object[]> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_HOURLY_TRAFFIC)) {

            ps.setInt(1, hoursBack);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Object[]{rs.getString("hour_bucket"), rs.getLong("request_count")});
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query hourly traffic: {}", e.getMessage(), e);
        }
        return result;
    }

    public List<Object[]> getTopEndpoints(int limit) {
        List<Object[]> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_TOP_ENDPOINTS)) {

            ps.setInt(1, Math.min(limit, 50));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Object[]{rs.getString("endpoint"), rs.getLong("hit_count")});
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query top endpoints: {}", e.getMessage(), e);
        }
        return result;
    }

    private AccessLog mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("accessed_at");
        return AccessLog.builder()
                .id(rs.getLong("id"))
                .ipAddress(rs.getString("ip_address"))
                .requestMethod(rs.getString("request_method"))
                .endpoint(rs.getString("endpoint"))
                .userAgent(rs.getString("user_agent"))
                .sessionId(rs.getString("session_id"))
                .referer(rs.getString("referer"))
                .countryCode(rs.getString("country_code"))
                .responseTimeMs(getIntOrNull(rs, "response_time_ms"))
                .statusCode(rs.getInt("status_code"))
                .bytesSent(getIntOrNull(rs, "bytes_sent"))
                .accessedAt(ts != null ? ts.toLocalDateTime() : null)
                .build();
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    private Integer getIntOrNull(ResultSet rs, String column) throws SQLException {
        int val = rs.getInt(column);
        return rs.wasNull() ? null : val;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
