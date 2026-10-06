package com.example.was.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * 요청 라인/헤더/바디를 모두 바이트 단위로 읽는다.
 * Content-Length 는 바이트 수이므로, 문자 단위로 읽는 Reader 로는 바디 경계를 정확히 맞출 수 없다.
 */
public class HttpRequestParser {

    static final int MAX_LINE_BYTES = 8 * 1024;
    static final int MAX_HEADER_COUNT = 100;
    static final int DEFAULT_MAX_BODY_BYTES = 1024 * 1024;

    private static final String HDR_CONTENT_LENGTH = "Content-Length";
    private static final String HDR_TRANSFER_ENCODING = "Transfer-Encoding";
    private static final byte[] EMPTY_BODY = new byte[0];

    private final int maxBodyBytes;

    public HttpRequestParser() {
        this(DEFAULT_MAX_BODY_BYTES);
    }

    public HttpRequestParser(int maxBodyBytes) {
        if (maxBodyBytes < 0) {
            throw new IllegalArgumentException("maxBodyBytes must not be negative: " + maxBodyBytes);
        }
        this.maxBodyBytes = maxBodyBytes;
    }

    public HttpRequest parse(InputStream in) throws IOException {
        RequestLine rl = parseRequestLine(in);
        Map<String, String> headers = parseHeaders(in);
        byte[] body = readBody(in, headers);
        return new HttpRequest(rl.method(), rl.path(), rl.version(), headers, body);
    }

    private RequestLine parseRequestLine(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null) {
            throw new IOException("connection closed");
        }
        if (line.isBlank()) {
            throw HttpParseException.badRequest("empty request line");
        }
        String[] parts = line.split(" ", 3);
        if (parts.length != 3) {
            throw HttpParseException.badRequest("malformed request line: " + line);
        }
        return new RequestLine(parts[0], parts[1], parts[2]);
    }

    private record RequestLine(String method, String path, String version) {}

    private Map<String, String> parseHeaders(InputStream in) throws IOException {
        // 헤더 이름은 대소문자를 구분하지 않는다(RFC 9110).
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int count = 0;
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            if (++count > MAX_HEADER_COUNT) {
                throw HttpParseException.badRequest("too many headers");
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                throw HttpParseException.badRequest("header line without colon: " + line);
            }
            String name = line.substring(0, colon);
            // "Host : a.com" 처럼 이름에 공백이 섞이거나, 공백으로 시작하는 줄(obs-fold)은
            // 프록시마다 해석이 달라 스머글링에 악용될 수 있으므로 거절한다(RFC 9112 5.1, 5.2).
            if (name.isEmpty() || name.chars().anyMatch(c -> c == ' ' || c == '\t')) {
                throw HttpParseException.badRequest("invalid header name: " + name);
            }
            String value = line.substring(colon + 1).trim();

            // Content-Length 가 서로 다른 값으로 여러 번 오면 바디 경계를 확정할 수 없다(요청 스머글링 방어).
            String previous = headers.put(name, value);
            if (previous != null && name.equalsIgnoreCase(HDR_CONTENT_LENGTH) && !previous.equals(value)) {
                throw HttpParseException.badRequest("conflicting Content-Length");
            }
        }
        if (line == null) {
            throw new IOException("connection closed while reading headers");
        }
        return headers;
    }

    private byte[] readBody(InputStream in, Map<String, String> headers) throws IOException {
        String contentLength = headers.get(HDR_CONTENT_LENGTH);
        String transferEncoding = headers.get(HDR_TRANSFER_ENCODING);

        if (transferEncoding != null) {
            // 둘 다 있으면 프록시와 서버가 서로 다른 헤더로 경계를 해석할 수 있다(RFC 9112 6.1).
            if (contentLength != null) {
                throw HttpParseException.badRequest("both Content-Length and Transfer-Encoding present");
            }
            throw new HttpParseException(HttpStatus.NOT_IMPLEMENTED, "unsupported Transfer-Encoding: " + transferEncoding);
        }
        // 요청은 두 헤더가 모두 없으면 바디 길이가 0 이다.
        if (contentLength == null) {
            return EMPTY_BODY;
        }

        int length = parseContentLength(contentLength);
        byte[] body = in.readNBytes(length);
        if (body.length < length) {
            throw new IOException("connection closed while reading body");
        }
        return body;
    }

    private int parseContentLength(String value) {
        if (value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw HttpParseException.badRequest("invalid Content-Length: " + value);
        }
        // 자릿수로 먼저 걸러 long 오버플로를 피하고, 상한 초과는 바디를 읽기 전에 거절한다.
        if (value.length() > 10 || Long.parseLong(value) > maxBodyBytes) {
            throw new HttpParseException(HttpStatus.CONTENT_TOO_LARGE, "Content-Length too large: " + value);
        }
        return Integer.parseInt(value);
    }

    /**
     * LF 까지 읽어 한 줄을 반환한다. 앞의 CR 은 제거한다(RFC 9112 2.2: 단독 LF 도 줄 끝으로 인정 가능).
     * 줄 시작 전에 스트림이 끝나면 null 을 반환한다.
     */
    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                return toLine(buffer);
            }
            if (buffer.size() >= MAX_LINE_BYTES) {
                throw HttpParseException.badRequest("line too long");
            }
            buffer.write(b);
        }
        if (buffer.size() == 0) {
            return null;
        }
        throw new IOException("connection closed in the middle of a line");
    }

    private String toLine(ByteArrayOutputStream buffer) {
        byte[] bytes = buffer.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }
        for (int i = 0; i < length; i++) {
            // 줄 중간의 단독 CR 은 구현마다 해석이 달라 스머글링에 악용될 수 있으므로 거절한다.
            if (bytes[i] == '\r') {
                throw HttpParseException.badRequest("bare CR in line");
            }
        }
        // 헤더는 ISO-8859-1 로 디코딩한다. 바이트와 문자가 1:1 대응해 어떤 바이트도 손실되지 않는다.
        return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
    }
}