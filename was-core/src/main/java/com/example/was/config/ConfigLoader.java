package com.example.was.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 20;
    private static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_THREAD_POOL_CORE = 10;
    private static final int DEFAULT_THREAD_POOL_MAX = 200;
    private static final int DEFAULT_THREAD_POOL_KEEP_ALIVE_SECONDS = 60;
    private static final int DEFAULT_THREAD_POOL_QUEUE_SIZE = 100;

    private static final List<String> DEFAULT_BLOCKED_EXTENSIONS = List.of(".exe");

    public ServerConfig load(Path path) throws IOException {
        JsonNode root = MAPPER.readTree(path.toFile());
        return new ServerConfig(
                parsePort(root),
                parseKeepAliveTimeout(root),
                parseShutdownTimeout(root),
                parseBlockedExtensions(root),
                parseThreadPool(root),
                parseVirtualHosts(root)
        );
    }

    private int parsePort(JsonNode root) {
        return root.get("port").asInt();
    }

    private int parseKeepAliveTimeout(JsonNode root) {
        return root.path("keepAliveTimeoutSeconds").asInt(DEFAULT_KEEP_ALIVE_SECONDS);
    }

    private int parseShutdownTimeout(JsonNode root) {
        return root.path("shutdownTimeoutSeconds").asInt(DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
    }

    private List<String> parseBlockedExtensions(JsonNode root) {
        JsonNode node = root.path("blockedExtensions");
        if (node.isMissingNode()) return DEFAULT_BLOCKED_EXTENSIONS;
        List<String> result = new ArrayList<>();
        node.forEach(n -> result.add(n.asText()));
        return result;
    }

    private ThreadPoolConfig parseThreadPool(JsonNode root) {
        JsonNode node = root.path("threadPool");
        return new ThreadPoolConfig(
                node.path("coreSize").asInt(DEFAULT_THREAD_POOL_CORE),
                node.path("maxSize").asInt(DEFAULT_THREAD_POOL_MAX),
                node.path("keepAliveSeconds").asInt(DEFAULT_THREAD_POOL_KEEP_ALIVE_SECONDS),
                node.path("queueSize").asInt(DEFAULT_THREAD_POOL_QUEUE_SIZE)
        );
    }

    private List<VirtualHostConfig> parseVirtualHosts(JsonNode root) {
        List<VirtualHostConfig> virtualHosts = new ArrayList<>();
        for (JsonNode vhostNode : root.get("virtualHosts")) {
            String host = vhostNode.get("host").asText();
            String httpRoot = vhostNode.get("httpRoot").asText();

            Map<Integer, String> errorPages = new LinkedHashMap<>();
            JsonNode errorPagesNode = vhostNode.path("errorPages");
            errorPagesNode.fields().forEachRemaining(entry ->
                    errorPages.put(Integer.parseInt(entry.getKey()), entry.getValue().asText())
            );

            virtualHosts.add(new VirtualHostConfig(host, httpRoot, errorPages));
        }
        return virtualHosts;
    }
}
