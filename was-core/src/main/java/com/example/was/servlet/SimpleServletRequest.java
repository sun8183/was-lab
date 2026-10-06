package com.example.was.servlet;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class SimpleServletRequest implements ServletRequest {

    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final String HDR_CONTENT_TYPE = "Content-Type";

    private static final byte[] EMPTY_BODY = new byte[0];

    private final String method;
    private final Map<String, String> parameters;
    private final Map<String, String> headers;
    private final byte[] body;

    public SimpleServletRequest(String method, String rawPath) {
        this(method, rawPath, Map.of(), EMPTY_BODY);
    }

    public SimpleServletRequest(String method, String rawPath, Map<String, String> headers, byte[] body) {
        this.method = method;
        // 파서가 만든 헤더 맵이 어떤 구현이든 이름 조회가 대소문자를 구분하지 않도록 다시 담는다.
        Map<String, String> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitive.putAll(headers);
        this.headers = Collections.unmodifiableMap(caseInsensitive);
        this.body = body;
        this.parameters = parseParameters(rawPath);
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getParameter(String name) {
        return parameters.get(name);
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public byte[] getBody() {
        return body;
    }

    /**
     * 쿼리스트링과 폼 바디(POST + application/x-www-form-urlencoded)를 합친다.
     * 같은 키가 양쪽에 있으면 쿼리스트링 값이 우선한다(서블릿 스펙과 같은 순서).
     */
    private Map<String, String> parseParameters(String rawPath) {
        Map<String, String> result = new LinkedHashMap<>();
        int questionMark = rawPath.indexOf('?');
        if (questionMark >= 0) {
            parsePairs(rawPath.substring(questionMark + 1), CHARSET, result);
        }

        if (isFormPost()) {
            Charset charset = formCharset();
            Map<String, String> form = new LinkedHashMap<>();
            parsePairs(new String(body, charset), charset, form);
            form.forEach(result::putIfAbsent);
        }
        return result;
    }

    // 폼 바디 파싱은 POST 에만 적용한다(Tomcat 기본값 parseBodyMethods="POST" 와 같음).
    private boolean isFormPost() {
        String contentType = headers.get(HDR_CONTENT_TYPE);
        if (!"POST".equals(method) || contentType == null) {
            return false;
        }
        int semicolon = contentType.indexOf(';');
        String mediaType = (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim();
        return FORM_CONTENT_TYPE.equalsIgnoreCase(mediaType);
    }

    // Content-Type 의 charset 파라미터를 따르고, 없거나 알 수 없는 값이면 UTF-8 을 쓴다.
    private Charset formCharset() {
        String contentType = headers.get(HDR_CONTENT_TYPE);
        for (String param : contentType.split(";")) {
            String trimmed = param.trim();
            if (trimmed.regionMatches(true, 0, "charset=", 0, "charset=".length())) {
                String name = trimmed.substring("charset=".length()).replace("\"", "").trim();
                try {
                    return Charset.forName(name);
                } catch (IllegalArgumentException e) {
                    return CHARSET;
                }
            }
        }
        return CHARSET;
    }

    private static void parsePairs(String encoded, Charset charset, Map<String, String> result) {
        for (String pair : encoded.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            try {
                result.put(URLDecoder.decode(key, charset), URLDecoder.decode(value, charset));
            } catch (IllegalArgumentException e) {
                // "%zz" 처럼 잘못된 인코딩은 클라이언트 오류이므로 500 대신 해당 쌍만 무시한다.
            }
        }
    }
}