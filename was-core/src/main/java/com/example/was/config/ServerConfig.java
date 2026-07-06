package com.example.was.config;

import java.util.List;

public record ServerConfig(int port, int keepAliveTimeoutSeconds, int shutdownTimeoutSeconds,
                           List<String> blockedExtensions,
                           ThreadPoolConfig threadPool,
                           List<VirtualHostConfig> virtualHosts) {

    /**
     * Resolves a virtual host by the raw Host header value (may include ":port").
     * Falls back to the first configured virtual host when no exact match is found,
     * mirroring Apache's default-vhost behavior.
     */
    public VirtualHostConfig resolveVirtualHost(String hostHeaderValue) {
        String hostName = stripPort(hostHeaderValue);
        return virtualHosts.stream()
                .filter(vhost -> vhost.host().equalsIgnoreCase(hostName))
                .findFirst()
                .or(() -> virtualHosts.stream().findFirst())
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
