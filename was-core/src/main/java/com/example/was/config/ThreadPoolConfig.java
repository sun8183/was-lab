package com.example.was.config;

public record ThreadPoolConfig(int coreSize, int maxSize, int keepAliveSeconds, int queueSize) {
}
