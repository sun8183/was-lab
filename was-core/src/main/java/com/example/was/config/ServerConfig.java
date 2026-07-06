package com.example.was.config;

import java.util.List;
import java.util.Map;

public record ServerConfig(int port, int keepAliveTimeoutSeconds, int shutdownTimeoutSeconds,
                           List<String> blockedExtensions,
                           ThreadPoolConfig threadPool,
                           Map<String, VirtualHostConfig> virtualHosts) {

    /**
     * Resolves a virtual host by the raw Host header value (may include ":port").
     * Falls back to the first configured virtual host when no exact match is found,
     * mirroring Apache's default-vhost behavior.
     */
    public VirtualHostConfig resolveVirtualHost(String hostHeaderValue) {
        String hostName = stripPort(hostHeaderValue).toLowerCase();
        VirtualHostConfig matched = virtualHosts.get(hostName);
        if (matched != null) {
            return matched;
        }
        return virtualHosts.values().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("no virtual hosts configured"));
    }

    private static String stripPort(String hostHeaderValue) {
        if (hostHeaderValue == null) {
            return "";
        }
        int colon = hostHeaderValue.indexOf(':');
        return colon < 0 ? hostHeaderValue : hostHeaderValue.substring(0, colon);
    }
}
