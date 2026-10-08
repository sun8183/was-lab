package com.example.was;

import com.example.was.config.ServerConfig;
import com.example.was.config.ThreadPoolConfig;
import com.example.was.config.VirtualHostConfig;
import com.example.was.http.HttpMethod;
import com.example.was.http.HttpRequest;
import com.example.was.http.HttpStatus;
import com.example.was.security.BlockedExtensionRule;
import com.example.was.servlet.ServletInitException;
import com.example.was.servlet.ServletMapper;
import com.example.was.servlet.SimpleServlet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class RequestDispatcherMethodTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // 메서드와 바디를 그대로 돌려주는 테스트용 서블릿. /Echo 경로에만 매핑된다.
    private final SimpleServlet echoServlet = (req, res) ->
            res.getWriter().write(req.getMethod() + ":" + new String(req.getBody(), StandardCharsets.UTF_8));
    private final ServletMapper mapper = path -> path.startsWith("/Echo") ? Optional.of(echoServlet) : Optional.empty();

    private RequestDispatcher dispatcher;

    @Before
    public void setUp() throws IOException {
        Files.writeString(tmp.getRoot().toPath().resolve("index.html"), "<html>index</html>");
        Files.writeString(tmp.getRoot().toPath().resolve("app.exe"), "binary");
        VirtualHostConfig vhost = new VirtualHostConfig("a.com", tmp.getRoot().getAbsolutePath(), Map.of());
        ServerConfig config = new ServerConfig(8080, 20, 30, 1024 * 1024, List.of(),
                new ThreadPoolConfig(10, 200, 60, 100), Map.of("a.com", vhost));
        HttpResponseWriter writer = new HttpResponseWriter(20);
        StaticFileHandler staticFileHandler = new StaticFileHandler(List.of(new BlockedExtensionRule(List.of(".exe"))));
        dispatcher = new RequestDispatcher(config, mapper, staticFileHandler, writer);
    }

    private static HttpRequest request(String method, String path, String body) {
        return new HttpRequest(method, path, "HTTP/1.1", Map.of("Host", "a.com"), body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void postToServletDeliversMethodAndBody() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpStatus status = dispatcher.dispatch(out, request("POST", "/Echo", "name=김철수"), false);

        assertEquals(HttpStatus.OK, status);
        assertTrue(out.toString(StandardCharsets.UTF_8).endsWith("POST:name=김철수"));
    }

    @Test
    public void getStaticFileStillWorks() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpStatus status = dispatcher.dispatch(out, request("GET", "/index.html", ""), false);

        assertEquals(HttpStatus.OK, status);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("<html>index</html>"));
    }

    @Test
    public void postToStaticFileIs405WithAllowHeader() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpStatus status = dispatcher.dispatch(out, request("POST", "/index.html", "x=1"), false);

        String res = out.toString(StandardCharsets.UTF_8);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, status);
        assertTrue(res.startsWith("HTTP/1.1 405"));
        assertTrue(res.contains("Allow: GET, HEAD\r\n"));
        assertFalse(res.contains("<html>index</html>"));
    }

    @Test
    public void postToMissingPathIs404Not405() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpStatus status = dispatcher.dispatch(out, request("POST", "/missing.html", "x=1"), false);

        assertEquals(HttpStatus.NOT_FOUND, status);
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("HTTP/1.1 404"));
    }

    @Test
    public void postToForbiddenPathIs403Not405() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpStatus status = dispatcher.dispatch(out, request("POST", "/app.exe", "x=1"), false);

        assertEquals(HttpStatus.FORBIDDEN, status);
    }

    @Test
    public void postWithMatchingEtagIs405Not304() throws IOException {
        ByteArrayOutputStream getOut = new ByteArrayOutputStream();
        dispatcher.dispatch(getOut, request("GET", "/index.html", ""), false);
        String etag = getOut.toString(StandardCharsets.UTF_8).lines()
                .filter(l -> l.startsWith("ETag: ")).findFirst().orElseThrow().substring("ETag: ".length());

        HttpRequest post = new HttpRequest("POST", "/index.html", "HTTP/1.1",
                Map.of("Host", "a.com", "If-None-Match", etag), new byte[0]);
        HttpStatus status = dispatcher.dispatch(new ByteArrayOutputStream(), post, false);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, status);
    }

    @Test
    public void headStaticFileSendsHeadersWithContentLengthButNoBody() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpStatus status = dispatcher.dispatch(out, request("HEAD", "/index.html", ""), false);

        String res = out.toString(StandardCharsets.UTF_8);
        assertEquals(HttpStatus.OK, status);
        assertTrue(res.contains("Content-Length: " + "<html>index</html>".length() + "\r\n"));
        assertTrue(res.endsWith("\r\n\r\n"));
        assertFalse(res.contains("<html>index</html>"));
    }

    @Test
    public void headServletRunsServiceButSendsNoBody() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpStatus status = dispatcher.dispatch(out, request("HEAD", "/Echo", ""), false);

        String res = out.toString(StandardCharsets.UTF_8);
        assertEquals(HttpStatus.OK, status);
        assertTrue(res.contains("Content-Length: " + "HEAD:".length() + "\r\n"));
        assertTrue(res.endsWith("\r\n\r\n"));
    }

    @Test
    public void headNotFoundSendsNoBody() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpStatus status = dispatcher.dispatch(out, request("HEAD", "/missing.html", ""), false);

        String res = out.toString(StandardCharsets.UTF_8);
        assertEquals(HttpStatus.NOT_FOUND, status);
        assertTrue(res.startsWith("HTTP/1.1 404"));
        assertTrue(res.endsWith("\r\n\r\n"));
    }

    @Test
    public void servletInitFailureIs500NotFallbackTo404() throws IOException {
        ServletMapper failingMapper = path -> {
            throw new ServletInitException("Broken", new IllegalStateException("init failure"));
        };
        VirtualHostConfig vhost = new VirtualHostConfig("a.com", tmp.getRoot().getAbsolutePath(), Map.of());
        ServerConfig config = new ServerConfig(8080, 20, 30, 1024 * 1024, List.of(),
                new ThreadPoolConfig(10, 200, 60, 100), Map.of("a.com", vhost));
        RequestDispatcher failing = new RequestDispatcher(config, failingMapper,
                new StaticFileHandler(List.of()), new HttpResponseWriter(20));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpStatus status = failing.dispatch(out, request("GET", "/Broken", ""), false);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, status);
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("HTTP/1.1 500"));
    }

    @Test
    public void methodNamesAreCaseSensitive() {
        assertTrue(HttpMethod.isSupported("POST"));
        assertTrue(HttpMethod.isSupported("HEAD"));
        assertFalse(HttpMethod.isSupported("post"));
        assertFalse(HttpMethod.isSupported("PUT"));
    }
}