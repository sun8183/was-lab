package com.example.was;

import com.example.was.http.HttpMethod;
import com.example.was.http.HttpRequest;
import com.example.was.http.HttpRequestParser;
import com.example.was.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ConnectionHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String TEXT_PLAIN    = "text/plain; charset=UTF-8";
    private static final String HDR_CONNECTION = "Connection";
    private static final String CONNECTION_CLOSE = "close";

    private final Socket socket;
    private final HttpRequestParser parser;
    private final RequestDispatcher dispatcher;
    private final HttpResponseWriter responseWriter;

    public ConnectionHandler(Socket socket, HttpRequestParser parser,
                             RequestDispatcher dispatcher, HttpResponseWriter responseWriter) {
        this.socket = socket;
        this.parser = parser;
        this.dispatcher = dispatcher;
        this.responseWriter = responseWriter;
    }

    @Override
    public void run() {
        String clientIp = socket.getInetAddress().getHostAddress();
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), CHARSET));
             OutputStream out = socket.getOutputStream()) {

            while (handleRequest(reader, out, clientIp)) { /* keep-alive */ }

        } catch (IOException e) {
            log.error("connection error", e);
        }
    }

    private boolean handleRequest(BufferedReader reader, OutputStream out, String clientIp) throws IOException {
        HttpRequest request;
        try {
            request = parser.parse(reader);
        } catch (IllegalArgumentException e) {
            log.warn("{} malformed request line: {}", clientIp, e.getMessage());
            responseWriter.writeResponse(out, HttpStatus.BAD_REQUEST, TEXT_PLAIN,
                    HttpStatus.BAD_REQUEST.bodyBytes(CHARSET), false, null);
            return false;
        } catch (IOException e) {
            return false;
        }

        boolean keepAlive = !CONNECTION_CLOSE.equalsIgnoreCase(request.headers().get(HDR_CONNECTION));

        if (!HttpMethod.isSupported(request.method())) {
            logAccess(clientIp, request, HttpStatus.NOT_IMPLEMENTED);
            responseWriter.writeResponse(out, HttpStatus.NOT_IMPLEMENTED, TEXT_PLAIN,
                    HttpStatus.NOT_IMPLEMENTED.bodyBytes(CHARSET), keepAlive, null);
            return keepAlive;
        }

        try {
            HttpStatus status = dispatcher.dispatch(out, request, keepAlive);
            logAccess(clientIp, request, status);
        } catch (IOException e) {
            logAccess(clientIp, request, HttpStatus.INTERNAL_SERVER_ERROR, e);
            try {
                // 500 응답을 쓰는 도중 클라이언트가 연결을 끊으면 또 IOException이 발생할 수 있으므로 무시한다.
                responseWriter.writeResponse(out, HttpStatus.INTERNAL_SERVER_ERROR, TEXT_PLAIN,
                        HttpStatus.INTERNAL_SERVER_ERROR.bodyBytes(CHARSET), false, null);
            } catch (IOException ignored) {}
            return false;
        }

        return keepAlive;
    }

    private void logAccess(String clientIp, HttpRequest request, HttpStatus status) {
        if (status.code >= 500) {
            log.error("{} {} {} -> {}", clientIp, request.method(), request.path(), status.code);
        } else if (status.code >= 400) {
            log.warn("{} {} {} -> {}", clientIp, request.method(), request.path(), status.code);
        } else {
            log.info("{} {} {} -> {}", clientIp, request.method(), request.path(), status.code);
        }
    }

    private void logAccess(String clientIp, HttpRequest request, HttpStatus status, Exception e) {
        log.error("{} {} {} -> {}", clientIp, request.method(), request.path(), status.code, e);
    }
}