package com.example.was;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.was.config.ServerConfig;
import com.example.was.config.ThreadPoolConfig;
import com.example.was.config.VirtualHostConfig;
import com.example.was.http.HttpRequestParser;
import com.example.was.security.BlockedExtensionRule;
import com.example.was.security.PathTraversalRule;
import com.example.was.servlet.DirectClassServletMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LogLevelRoutingTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private ListAppender<ILoggingEvent> listAppender;
    private Logger handlerLogger;
    private ServerSocket serverSocket;
    private ServerConfig config;

    @Before
    public void setUp() throws IOException {
        handlerLogger = (Logger) LoggerFactory.getLogger(ConnectionHandler.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        handlerLogger.addAppender(listAppender);

        serverSocket = new ServerSocket(0);

        VirtualHostConfig vhost = new VirtualHostConfig(
                "localhost", tmp.getRoot().getAbsolutePath(), Map.of());
        config = new ServerConfig(serverSocket.getLocalPort(), 20, 30, List.of(".exe"),
                new ThreadPoolConfig(10, 200, 60, 100), Map.of("localhost", vhost));
    }

    @After
    public void tearDown() throws IOException {
        handlerLogger.detachAppender(listAppender);
        serverSocket.close();
    }

    private ConnectionHandler createHandler(Socket serverSideSocket) {
        StaticFileHandler sfh = new StaticFileHandler(
                List.of(new PathTraversalRule(), new BlockedExtensionRule(Set.of(".exe"))));
        HttpResponseWriter writer = new HttpResponseWriter(20);
        RequestDispatcher dispatcher = new RequestDispatcher(
                config, new DirectClassServletMapper(), sfh, writer);
        return new ConnectionHandler(serverSideSocket, new HttpRequestParser(), dispatcher, writer);
    }

    private void sendRequest(String rawRequest) throws IOException {
        try (Socket client = new Socket("localhost", serverSocket.getLocalPort())) {
            client.getOutputStream().write(rawRequest.getBytes(StandardCharsets.UTF_8));
            client.getOutputStream().flush();
            try (Socket server = serverSocket.accept()) {
                createHandler(server).run();
            }
        }
    }

    private ILoggingEvent findEvent(String pathFragment) {
        return listAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains(pathFragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError("log event not found for: " + pathFragment));
    }

    @Test
    public void successResponseLogsInfo() throws IOException {
        File index = tmp.newFile("index.html");
        Files.writeString(index.toPath(), "<html>OK</html>", StandardCharsets.UTF_8);

        sendRequest("GET /index.html HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertEquals(Level.INFO, findEvent("/index.html").getLevel());
    }

    @Test
    public void notFoundLogsWarn() throws IOException {
        sendRequest("GET /missing.html HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertEquals(Level.WARN, findEvent("/missing.html").getLevel());
    }

    @Test
    public void pathTraversalLogsWarn() throws IOException {
        sendRequest("GET /../../../etc/passwd HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        ILoggingEvent event = findEvent("/etc/passwd");
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("403"));
    }

    @Test
    public void blockedExtensionLogsWarn() throws IOException {
        tmp.newFile("malware.exe");

        sendRequest("GET /malware.exe HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        ILoggingEvent event = findEvent("/malware.exe");
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("403"));
    }
}
