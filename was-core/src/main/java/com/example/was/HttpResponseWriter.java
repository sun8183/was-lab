package com.example.was;

import com.example.was.config.VirtualHostConfig;
import com.example.was.http.HttpStatus;

import java.io.IOException;
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
        String connection = keepAlive ? CONNECTION_KEEP_ALIVE : CONNECTION_CLOSE;
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(status.code).append(" ").append(status.reason).append("\r\n");
        sb.append("Content-Type: ").append(contentType).append("\r\n");
        sb.append("Content-Length: ").append(body.length).append("\r\n");
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
        out.write(body);
        out.flush();
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