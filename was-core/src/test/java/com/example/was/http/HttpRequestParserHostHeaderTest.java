package com.example.was.http;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import static org.junit.Assert.*;

public class HttpRequestParserHostHeaderTest {

    private final HttpRequestParser parser = new HttpRequestParser();

    private HttpRequest parse(String raw) throws IOException {
        return parser.parse(new BufferedReader(new StringReader(raw)));
    }

    @Test
    public void parsePlainHost() throws IOException {
        HttpRequest req = parse("GET / HTTP/1.1\r\nHost: a.com\r\n\r\n");
        assertEquals("a.com", req.headers().get("Host"));
    }

    @Test
    public void parseHostWithPort() throws IOException {
        HttpRequest req = parse("GET / HTTP/1.1\r\nHost: a.com:8080\r\n\r\n");
        assertEquals("a.com:8080", req.headers().get("Host"));
    }

    @Test
    public void parseMultipleHeadersHostPresent() throws IOException {
        HttpRequest req = parse("GET /index.html HTTP/1.1\r\nHost: b.com\r\nConnection: keep-alive\r\n\r\n");
        assertEquals("b.com", req.headers().get("Host"));
        assertEquals("keep-alive", req.headers().get("Connection"));
    }

    @Test
    public void noHostHeaderReturnsNull() throws IOException {
        HttpRequest req = parse("GET / HTTP/1.1\r\nConnection: close\r\n\r\n");
        assertNull(req.headers().get("Host"));
    }
}
