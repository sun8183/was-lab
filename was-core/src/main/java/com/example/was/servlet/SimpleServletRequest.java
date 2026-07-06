package com.example.was.servlet;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleServletRequest implements ServletRequest {

    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private final String method;
    private final Map<String, String> parameters;

    public SimpleServletRequest(String method, String rawPath) {
        this.method = method;
        this.parameters = parseQueryString(rawPath);
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getParameter(String name) {
        return parameters.get(name);
    }

    private static Map<String, String> parseQueryString(String rawPath) {
        Map<String, String> result = new LinkedHashMap<>();
        int questionMark = rawPath.indexOf('?');
        if (questionMark < 0 || questionMark == rawPath.length() - 1) {
            return result;
        }

        String query = rawPath.substring(questionMark + 1);
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            result.put(decode(key), decode(value));
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, CHARSET);
    }
}
