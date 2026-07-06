package com.example.was;

import com.example.was.config.VirtualHostConfig;
import com.example.was.http.HttpStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.Assert.*;

public class HttpResponseWriterErrorPageTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private HttpResponseWriter writer;
    private VirtualHostConfig vhost;

    @Before
    public void setUp() throws IOException {
        writer = new HttpResponseWriter(20);

        writeFile("403.html", "<html>403 Forbidden</html>");
        writeFile("404.html", "<html>404 Not Found</html>");
        writeFile("500.html", "<html>500 Internal Server Error</html>");

        vhost = new VirtualHostConfig(
                "a.com",
                tmp.getRoot().getAbsolutePath(),
                Map.of(403, "403.html", 404, "404.html", 500, "500.html")
        );
    }

    private void writeFile(String name, String content) throws IOException {
        File f = tmp.newFile(name);
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
    }

    private String response(HttpStatus status) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeErrorResponse(out, vhost, status, false);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    public void forbidden403ReturnsConfiguredHtml() throws IOException {
        String res = response(HttpStatus.FORBIDDEN);
        assertTrue(res.contains("HTTP/1.1 403"));
        assertTrue(res.contains("text/html"));
        assertTrue(res.contains("<html>403 Forbidden</html>"));
    }

    @Test
    public void notFound404ReturnsConfiguredHtml() throws IOException {
        String res = response(HttpStatus.NOT_FOUND);
        assertTrue(res.contains("HTTP/1.1 404"));
        assertTrue(res.contains("text/html"));
        assertTrue(res.contains("<html>404 Not Found</html>"));
    }

    @Test
    public void internalError500ReturnsConfiguredHtml() throws IOException {
        String res = response(HttpStatus.INTERNAL_SERVER_ERROR);
        assertTrue(res.contains("HTTP/1.1 500"));
        assertTrue(res.contains("text/html"));
        assertTrue(res.contains("<html>500 Internal Server Error</html>"));
    }

    @Test
    public void noErrorPageConfiguredFallsBackToPlainText() throws IOException {
        VirtualHostConfig noPages = new VirtualHostConfig("a.com", tmp.getRoot().getAbsolutePath(), Map.of());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeErrorResponse(out, noPages, HttpStatus.NOT_FOUND, false);
        String res = out.toString(StandardCharsets.UTF_8);
        assertTrue(res.contains("HTTP/1.1 404"));
        assertTrue(res.contains("text/plain"));
    }

    @Test
    public void missingErrorPageFileFallsBackToPlainText() throws IOException {
        VirtualHostConfig missing = new VirtualHostConfig(
                "a.com",
                tmp.getRoot().getAbsolutePath(),
                Map.of(404, "nonexistent.html")
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeErrorResponse(out, missing, HttpStatus.NOT_FOUND, false);
        String res = out.toString(StandardCharsets.UTF_8);
        assertTrue(res.contains("HTTP/1.1 404"));
        assertTrue(res.contains("text/plain"));
    }
}
