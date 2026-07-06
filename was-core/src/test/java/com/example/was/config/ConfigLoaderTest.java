package com.example.was.config;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class ConfigLoaderTest {

    private static ServerConfig config;

    @BeforeClass
    public static void load() throws IOException {
        Path configPath = Paths.get("../config.json");
        config = new ConfigLoader().load(configPath);
    }

    @Test
    public void portIs8080() {
        assertEquals(8080, config.port());
    }

    @Test
    public void twoVirtualHostsLoaded() {
        assertEquals(2, config.virtualHosts().size());
    }

    @Test
    public void aComHttpRoot() {
        VirtualHostConfig vhost = config.resolveVirtualHost("a.com");
        assertEquals("./webapp/a", vhost.httpRoot());
    }

    @Test
    public void bComHttpRoot() {
        VirtualHostConfig vhost = config.resolveVirtualHost("b.com");
        assertEquals("./webapp/b", vhost.httpRoot());
    }

    @Test
    public void aComErrorPages() {
        VirtualHostConfig vhost = config.resolveVirtualHost("a.com");
        assertEquals("403.html", vhost.errorPageFor(403));
        assertEquals("404.html", vhost.errorPageFor(404));
        assertEquals("500.html", vhost.errorPageFor(500));
    }

    @Test
    public void bComErrorPages() {
        VirtualHostConfig vhost = config.resolveVirtualHost("b.com");
        assertEquals("403.html", vhost.errorPageFor(403));
        assertEquals("404.html", vhost.errorPageFor(404));
        assertEquals("500.html", vhost.errorPageFor(500));
    }
}
