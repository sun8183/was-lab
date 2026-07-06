package com.example.was;

import com.example.was.config.ConfigLoader;
import com.example.was.config.ServerConfig;
import com.example.was.servlet.DirectClassServletMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String DEFAULT_CONFIG_PATH = "config.json";

    public static void main(String[] args) throws IOException {
        log.info("Loading config from {}", DEFAULT_CONFIG_PATH);

        ServerConfig config;
        try {
            config = new ConfigLoader().load(Path.of(DEFAULT_CONFIG_PATH));
        } catch (IOException | RuntimeException e) {
            log.error("Failed to load config from {}", DEFAULT_CONFIG_PATH, e);
            throw e;
        }

        new WebServer(config, new DirectClassServletMapper()).start();
    }
}
