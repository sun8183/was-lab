package com.example.was;

import com.example.was.config.VirtualHostConfig;
import com.example.was.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class HttpResponseWriter {

    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String TEXT_HTML  = "text/html; charset=UTF-8";
    private static final String TEXT_PLAIN = "text/plain; charset=UTF-8";
    private static final String CONNECTION_CLOSE      = "close";
    private static final String CONNECTION_KEEP_ALIVE = "keep-alive";

    private final int keepAliveTimeoutSeconds;

    public HttpResponseWriter(int keepAliveTimeoutSeconds) {
        this.keepAliveTimeoutSeconds = keepAliveTimeoutSeconds;
    }

    public void writeResponse(OutputStream out, HttpStatus status, String contentType, byte[] body, boolean keepAlive, String etag) throws IOException {
        writeHeaders(out, status, contentType, body.length, keepAlive, etag);
        out.write(body);
        out.flush();
    }

    // 정적 파일 서빙 전용 — 파일 전체를 byte[] 로 읽지 않고 스트리밍으로 전송해 대용량 파일에서도 메모리 사용량을 일정하게 유지한다.
    public void writeResponse(OutputStream out, HttpStatus status, String contentType, Path filePath, long contentLength, boolean keepAlive, String etag) throws IOException {
        writeHeaders(out, status, contentType, contentLength, keepAlive, etag);
        try (InputStream in = Files.newInputStream(filePath)) {
            in.transferTo(out);
        }
        out.flush();
    }

    private void writeHeaders(OutputStream out, HttpStatus status, String contentType, long contentLength, boolean keepAlive, String etag) throws IOException {
        String connection = keepAlive ? CONNECTION_KEEP_ALIVE : CONNECTION_CLOSE;
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(status.code).append(" ").append(status.reason).append("\r\n");
        sb.append("Content-Type: ").append(contentType).append("\r\n");
        sb.append("Content-Length: ").append(contentLength).append("\r\n");
        sb.append("Connection: ").append(connection).append("\r\n");
        if (keepAlive) {
            sb.append("Keep-Alive: timeout=").append(keepAliveTimeoutSeconds).append("\r\n");
        }
        if (etag != null) {
            sb.append("ETag: ").append(etag).append("\r\n");
            sb.append("Cache-Control: no-cache\r\n");
        }
        sb.append("\r\n");
        out.write(sb.toString().getBytes(CHARSET));
    }

    public void writeNotModified(OutputStream out, String etag, boolean keepAlive) throws IOException {
        String connection = keepAlive ? CONNECTION_KEEP_ALIVE : CONNECTION_CLOSE;
        String headers = "HTTP/1.1 304 Not Modified\r\n" +
                "ETag: " + etag + "\r\n" +
                "Connection: " + connection + "\r\n" +
                "\r\n";
        out.write(headers.getBytes(CHARSET));
        out.flush();
    }

    public void writeErrorResponse(OutputStream out, VirtualHostConfig vhost, HttpStatus status, boolean keepAlive) throws IOException {
        String errorPageName = vhost.errorPageFor(status.code);
        if (errorPageName != null) {
            Path httpRoot = Path.of(vhost.httpRoot()).toAbsolutePath().normalize();
            Path errorPage = httpRoot.resolve(errorPageName).normalize();
            if (errorPage.startsWith(httpRoot) && Files.isRegularFile(errorPage)) {
                byte[] body = Files.readAllBytes(errorPage);
                writeResponse(out, status, TEXT_HTML, body, keepAlive, null);
                return;
            }
        }
        writeResponse(out, status, TEXT_PLAIN, status.bodyBytes(CHARSET), keepAlive, null);
    }
}