// what it does: [IpStatistic entity, constructor, getters, toString]
package com.netwatch.model;

import java.time.LocalDateTime;

public final class IpStatistic {

    private final String        ipAddress;
    private final long          requestCount;
    private final LocalDateTime firstSeen;
    private final LocalDateTime lastSeen;
    private final String        mostVisitedEndpoint;
    private final String        countryCode;
    private final boolean       isBlocked;
    private final LocalDateTime updatedAt;

    public IpStatistic(
            String        ipAddress,
            long          requestCount,
            LocalDateTime firstSeen,
            LocalDateTime lastSeen,
            String        mostVisitedEndpoint,
            String        countryCode,
            boolean       isBlocked,
            LocalDateTime updatedAt) {
        this.ipAddress           = ipAddress;
        this.requestCount        = requestCount;
        this.firstSeen           = firstSeen;
        this.lastSeen            = lastSeen;
        this.mostVisitedEndpoint = mostVisitedEndpoint;
        this.countryCode         = countryCode;
        this.isBlocked           = isBlocked;
        this.updatedAt           = updatedAt;
    }

    public String        getIpAddress()           { return ipAddress; }
    public long          getRequestCount()         { return requestCount; }
    public LocalDateTime getFirstSeen()            { return firstSeen; }
    public LocalDateTime getLastSeen()             { return lastSeen; }
    public String        getMostVisitedEndpoint()  { return mostVisitedEndpoint; }
    public String        getCountryCode()          { return countryCode; }
    public boolean       isBlocked()               { return isBlocked; }
    public LocalDateTime getUpdatedAt()            { return updatedAt; }

    @Override
    public String toString() {
        return String.format("IpStatistic{ip='%s', count=%d, lastSeen=%s, blocked=%b}",
                ipAddress, requestCount, lastSeen, isBlocked);
    }
}
