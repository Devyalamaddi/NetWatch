// what it does: [AccessLog entity, Builder, getters, toString]
package com.netwatch.model;

import java.time.LocalDateTime;

public final class AccessLog {

    private final Long          id;
    private final String        ipAddress;
    private final String        requestMethod;
    private final String        endpoint;
    private final String        userAgent;
    private final String        sessionId;
    private final String        referer;
    private final String        countryCode;
    private final Integer       responseTimeMs;
    private final int           statusCode;
    private final Integer       bytesSent;
    private final LocalDateTime accessedAt;

    private AccessLog(Builder builder) {
        this.id             = builder.id;
        this.ipAddress      = builder.ipAddress;
        this.requestMethod  = builder.requestMethod;
        this.endpoint       = builder.endpoint;
        this.userAgent      = builder.userAgent;
        this.sessionId      = builder.sessionId;
        this.referer        = builder.referer;
        this.countryCode    = builder.countryCode;
        this.responseTimeMs = builder.responseTimeMs;
        this.statusCode     = builder.statusCode;
        this.bytesSent      = builder.bytesSent;
        this.accessedAt     = builder.accessedAt != null ? builder.accessedAt : LocalDateTime.now();
    }

    public Long          getId()             { return id; }
    public String        getIpAddress()      { return ipAddress; }
    public String        getRequestMethod()  { return requestMethod; }
    public String        getEndpoint()       { return endpoint; }
    public String        getUserAgent()      { return userAgent; }
    public String        getSessionId()      { return sessionId; }
    public String        getReferer()        { return referer; }
    public String        getCountryCode()    { return countryCode; }
    public Integer       getResponseTimeMs() { return responseTimeMs; }
    public int           getStatusCode()     { return statusCode; }
    public Integer       getBytesSent()      { return bytesSent; }
    public LocalDateTime getAccessedAt()     { return accessedAt; }

    @Override
    public String toString() {
        return String.format("AccessLog{ip='%s', method='%s', endpoint='%s', status=%d, at=%s}",
                ipAddress, requestMethod, endpoint, statusCode, accessedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long          id;
        private String        ipAddress      = "0.0.0.0";
        private String        requestMethod  = "GET";
        private String        endpoint       = "/";
        private String        userAgent;
        private String        sessionId;
        private String        referer;
        private String        countryCode;
        private Integer       responseTimeMs;
        private int           statusCode     = 200;
        private Integer       bytesSent;
        private LocalDateTime accessedAt;

        public Builder id(Long id)                      { this.id = id;                return this; }
        public Builder ipAddress(String ip)             { this.ipAddress = ip;          return this; }
        public Builder requestMethod(String method)     { this.requestMethod = method;  return this; }
        public Builder endpoint(String endpoint)        { this.endpoint = endpoint;     return this; }
        public Builder userAgent(String ua)             { this.userAgent = ua;          return this; }
        public Builder sessionId(String sid)            { this.sessionId = sid;         return this; }
        public Builder referer(String referer)          { this.referer = referer;       return this; }
        public Builder countryCode(String cc)           { this.countryCode = cc;        return this; }
        public Builder responseTimeMs(Integer ms)       { this.responseTimeMs = ms;     return this; }
        public Builder statusCode(int sc)               { this.statusCode = sc;         return this; }
        public Builder bytesSent(Integer bytes)         { this.bytesSent = bytes;       return this; }
        public Builder accessedAt(LocalDateTime ts)     { this.accessedAt = ts;         return this; }

        public AccessLog build() {
            if (ipAddress == null || ipAddress.isBlank()) {
                throw new IllegalStateException("AccessLog requires a non-blank ipAddress");
            }
            return new AccessLog(this);
        }
    }
}
