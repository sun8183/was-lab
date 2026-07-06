package com.example.was.config;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ServerConfigVirtualHostTest {

    private static final VirtualHostConfig VHOST_A = new VirtualHostConfig("a.com", "/var/www/a", Map.of());
    private static final VirtualHostConfig VHOST_B = new VirtualHostConfig("b.com", "/var/www/b", Map.of());

    private ServerConfig config(VirtualHostConfig... hosts) {
        Map<String, VirtualHostConfig> virtualHosts = new LinkedHashMap<>();
        for (VirtualHostConfig host : hosts) {
            virtualHosts.put(host.host().toLowerCase(), host);
        }
        return new ServerConfig(8080, 20, 30, List.of(), new ThreadPoolConfig(10, 200, 60, 100), virtualHosts);
    }

    @Test
    public void resolveByExactHost() {
        ServerConfig cfg = config(VHOST_A, VHOST_B);
        assertEquals(VHOST_A, cfg.resolveVirtualHost("a.com"));
        assertEquals(VHOST_B, cfg.resolveVirtualHost("b.com"));
    }

    @Test
    public void sameIpDifferentHostReturnsDifferentRoot() {
        ServerConfig cfg = config(VHOST_A, VHOST_B);
        assertNotEquals(
                cfg.resolveVirtualHost("a.com").httpRoot(),
                cfg.resolveVirtualHost("b.com").httpRoot()
        );
    }

    @Test
    public void stripPortBeforeMatch() {
        ServerConfig cfg = config(VHOST_A, VHOST_B);
        assertEquals(VHOST_A, cfg.resolveVirtualHost("a.com:8080"));
        assertEquals(VHOST_B, cfg.resolveVirtualHost("b.com:443"));
    }

    @Test
    public void caseInsensitiveMatch() {
        ServerConfig cfg = config(VHOST_A, VHOST_B);
        assertEquals(VHOST_A, cfg.resolveVirtualHost("A.COM"));
        assertEquals(VHOST_B, cfg.resolveVirtualHost("B.Com"));
    }

    @Test
    public void unknownHostFallsBackToFirst() {
        ServerConfig cfg = config(VHOST_A, VHOST_B);
        assertEquals(VHOST_A, cfg.resolveVirtualHost("unknown.com"));
    }

    @Test
    public void nullHostFallsBackToFirst() {
        ServerConfig cfg = config(VHOST_A, VHOST_B);
        assertEquals(VHOST_A, cfg.resolveVirtualHost(null));
    }

    @Test(expected = IllegalStateException.class)
    public void noVirtualHostsThrows() {
        config().resolveVirtualHost("a.com");
    }
}
