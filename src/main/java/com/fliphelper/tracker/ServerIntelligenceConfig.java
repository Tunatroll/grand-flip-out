package com.fliphelper.tracker;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Data
public class ServerIntelligenceConfig {
    // Feature toggles
    private boolean useServerSideIntelligence = true;  // Default: use server
    // Set at runtime from GrandFlipOutConfig.backendUrl()
    private String serverBaseUrl = "";
    private int serverTimeoutMs = 5000;  // 5 second timeout before fallback
    private boolean fallbackToLocalOnError = true;  // Always have a fallback
    
    // Algorithm feature flags
    private boolean enableSmartAdvisor = true;
    private boolean enableQuickFlipAnalyzer = true;
    private boolean enableMarketIntelligence = true;
    private boolean enableDumpKnowledge = true;
    
    // Caching
    private boolean cacheServerResults = true;
    private int cacheExpirySeconds = 30;  // 30 second TTL
    
    public ServerIntelligenceConfig() {
        log.info("ServerIntelligenceConfig initialized - server-side intelligence mode active");
    }
    
    
    public String getEndpointUrl(String endpoint) {
        return serverBaseUrl + endpoint;
    }
    
    
    public boolean isServerIntelligenceAvailable() {
        return useServerSideIntelligence && isServerHealthy();
    }
    
    
    private boolean isServerHealthy() {
        return true;
    }
    
    
    public String getFallbackDescription() {
        return fallbackToLocalOnError ? "LOCAL FALLBACK AVAILABLE" : "NO FALLBACK - SERVER REQUIRED";
    }
}
