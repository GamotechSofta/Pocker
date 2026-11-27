package com.poker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.operations")
public class OperationsProperties {
    private String baseUrl;
    private String internalKey;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getInternalKey() { return internalKey; }
    public void setInternalKey(String internalKey) { this.internalKey = internalKey; }

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && internalKey != null && !internalKey.isBlank();
    }
}
