package com.example.was;

import com.example.was.config.ServerConfig;
import com.example.was.http.HttpRequestParser;
import com.example.was.http.HttpStatus;
import com.example.was.security.BlockedExtensionRule;
import com.example.was.security.ForbiddenRule;
import com.example.was.security.PathTraversalRule;
import com.example.was.servlet.ServletMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WebServer {

    private static final Logger log = LoggerFactory.getLogger(WebServer.class);

    private final ServerConfig config;
    private final ServletMapper servletMapper;
    private final HttpRequestParser parser;
    private final RequestDispatcher dispatcher;
    private final HttpResponseWriter responseWriter;
    private final int socketTimeoutMillis;
    private volatile boolean shuttingDown = false;

    public WebServer(ServerConfig config, ServletMapper servletMapper) {
        this.config = config;
        int keepAliveTimeoutSeconds = config.keepAliveTimeoutSeconds();
        this.socketTimeoutMillis = keepAliveTimeoutSeconds * 1000;
        List<ForbiddenRule> rules = List.of(
                new PathTraversalRule(),
                new BlockedExtensionRule(config.blockedExtensions())
        );
        StaticFileHandler staticFileHandler = new StaticFileHandler(rules);
        this.responseWriter = new HttpResponseWriter(keepAliveTimeoutSeconds);
        this.dispatcher = new RequestDispatcher(config, servletMapper, staticFileHandler, responseWriter);
        this.parser = new HttpRequestParser();
        this.servletMapper = servletMapper;
    }

    public void start() throws IOException {
        ThreadPoolExecutor threadPool = createThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(config.port())) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(serverSocket, threadPool), "shutdown"));
            log.info("WAS started on port {}", config.port());
            acceptLoop(serverSocket, threadPool);
        }
    }

    private void acceptLoop(ServerSocket serverSocket, ThreadPoolExecutor threadPool) {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(socketTimeoutMillis);
                if (shuttingDown) {
                    sendServiceUnavailable(socket);
                } else {
                    try {
                        threadPool.execute(new ConnectionHandler(socket, parser, dispatcher, responseWriter));
                    } catch (RejectedExecutionException e) {
                        // 스레드 풀 큐까지 가득 찬 경우 503 반환
                        sendServiceUnavailable(socket);
                    }
                }
            } catch (IOException e) {
                // shutdown() 이 serverSocket.close() 를 호출하면 accept() 가 IOException 을 던진다.
                // 소켓이 닫힌 것이 원인이므로 정상 종료로 간주하고 루프를 빠져나간다.
                if (!serverSocket.isClosed()) {
                    log.error("accept error", e);
                }
            }
        }
    }

    private ThreadPoolExecutor createThreadPool() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                config.threadPool().coreSize(),
                config.threadPool().maxSize(),
                config.threadPool().keepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.threadPool().queueSize()),
                new ThreadPoolExecutor.AbortPolicy()
        );
        pool.prestartAllCoreThreads();
        return pool;
    }

    private void shutdown(ServerSocket serverSocket, ThreadPoolExecutor threadPool) {
        log.info("Shutting down...");
        shuttingDown = true;
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(config.shutdownTimeoutSeconds(), TimeUnit.SECONDS)) {
                log.warn("Timed out waiting for requests to complete, forcing shutdown");
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.error("Failed to close server socket", e);
        }
        servletMapper.destroyAll();
        log.info("Shutdown complete");
    }

    private void sendServiceUnavailable(Socket socket) {
        try (socket; OutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
            responseWriter.writeResponse(out, HttpStatus.SERVICE_UNAVAILABLE,
                    "text/plain; charset=UTF-8",
                    HttpStatus.SERVICE_UNAVAILABLE.bodyBytes(StandardCharsets.UTF_8), false, null);
        } catch (IOException ignored) {
            // 503 응답을 쓰는 도중 클라이언트가 이미 연결을 끊었을 수 있으므로 무시한다.
        }
    }
}