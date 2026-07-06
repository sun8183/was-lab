package com.example.was.config;

import java.util.Map;

public record VirtualHostConfig(String host, String httpRoot, Map<Integer, String> errorPages) {

    public String errorPageFor(int statusCode) {
        return errorPages.get(statusCode);
    }
}
