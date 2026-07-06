package com.example.was.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpRequestParser {

    public HttpRequest parse(BufferedReader reader) throws IOException {
        RequestLine rl = parseRequestLine(reader);
        return new HttpRequest(rl.method(), rl.path(), rl.version(), parseHeaders(reader));
    }

    private RequestLine parseRequestLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("connection closed");
        }
        if (line.isBlank()) {
            throw new IllegalArgumentException("empty request line");
        }
        String[] parts = line.split(" ", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("malformed request line: " + line);
        }
        return new RequestLine(parts[0], parts[1], parts[2]);
    }

    private record RequestLine(String method, String path, String version) {}

    private Map<String, String> parseHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isBlank()) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            headers.put(name, value);
        }
        return headers;
    }
}
