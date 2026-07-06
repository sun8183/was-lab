package com.example.was;

import com.example.was.config.VirtualHostConfig;
import com.example.was.http.HttpRequest;
import com.example.was.http.HttpStatus;
import com.example.was.security.ForbiddenRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class StaticFileHandler {

    private static final String HDR_IF_NONE_MATCH = "If-None-Match";

    private final List<ForbiddenRule> forbiddenRules;

    public StaticFileHandler(List<ForbiddenRule> forbiddenRules) {
        this.forbiddenRules = forbiddenRules;
    }

    public ServeResult resolve(VirtualHostConfig vhost, HttpRequest request) throws IOException {
        Path httpRoot = Path.of(vhost.httpRoot()).toAbsolutePath().normalize();
        Path resolved = resolveFilePath(httpRoot, request.path());

        checkAccess(httpRoot, resolved);

        String etag = computeEtag(resolved);
        if (etag.equals(request.headers().get(HDR_IF_NONE_MATCH))) {
            return ServeResult.notModified(etag);
        }

        byte[] body = Files.readAllBytes(resolved);
        return ServeResult.ok(contentTypeFor(resolved), body, etag);
    }

    private void checkAccess(Path httpRoot, Path resolved) {
        if (forbiddenRules.stream().anyMatch(rule -> rule.matches(httpRoot, resolved))) {
            throw new ForbiddenException();
        }
        if (!Files.isRegularFile(resolved)) {
            throw new NotFoundException();
        }
    }

    private Path resolveFilePath(Path httpRoot, String requestPath) {
        int query = requestPath.indexOf('?');
        String pathOnly = query < 0 ? requestPath : requestPath.substring(0, query);
        String relativePath = pathOnly.equals("/") ? "/index.html" : pathOnly;
        return httpRoot.resolve("." + relativePath).normalize();
    }

    private String computeEtag(Path path) throws IOException {
        long lastModified = Files.getLastModifiedTime(path).toMillis();
        long size = Files.size(path);
        return "\"" + Long.toHexString(lastModified) + "-" + Long.toHexString(size) + "\"";
    }

    private String contentTypeFor(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
        return switch (extension) {
            case "html", "htm" -> "text/html; charset=UTF-8";
            case "css"         -> "text/css; charset=UTF-8";
            case "js"          -> "application/javascript; charset=UTF-8";
            case "json"        -> "application/json; charset=UTF-8";
            case "png"         -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif"         -> "image/gif";
            case "svg"         -> "image/svg+xml";
            default            -> "application/octet-stream";
        };
    }

    public record ServeResult(HttpStatus status, String contentType, byte[] body, String etag) {
        static ServeResult ok(String contentType, byte[] body, String etag) {
            return new ServeResult(HttpStatus.OK, contentType, body, etag);
        }
        static ServeResult notModified(String etag) {
            return new ServeResult(HttpStatus.NOT_MODIFIED, null, null, etag);
        }
    }

    static class ForbiddenException extends RuntimeException {}
    static class NotFoundException extends RuntimeException {}
}