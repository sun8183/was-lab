package com.example.was;

import com.example.was.config.VirtualHostConfig;
import com.example.was.http.HttpRequest;
import com.example.was.http.HttpStatus;
import com.example.was.security.ForbiddenRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class StaticFileHandler {

    private static final String HDR_IF_NONE_MATCH = "If-None-Match";

    private final List<ForbiddenRule> forbiddenRules;

    public StaticFileHandler(List<ForbiddenRule> forbiddenRules) {
        this.forbiddenRules = forbiddenRules;
    }

    /**
     * 요청 경로에 해당하는 파일을 찾고 접근 가능 여부만 검사한다.
     * 금지 규칙에 걸리면 ForbiddenException(403), 파일이 없으면 NotFoundException(404).
     * 메서드 검사(405)보다 먼저 호출해야 없는 경로에 405 가 나가지 않는다.
     */
    public Path locate(VirtualHostConfig vhost, HttpRequest request) {
        Path httpRoot = Path.of(vhost.httpRoot()).toAbsolutePath().normalize();
        Path resolved = resolveFilePath(httpRoot, request.path());
        checkAccess(httpRoot, resolved);
        return resolved;
    }

    /** locate() 로 찾은 파일의 응답 정보(ETag, 304 여부, 크기)를 만든다. */
    public ServeResult serve(Path file, HttpRequest request) throws IOException {
        String etag = computeEtag(file);
        if (etag.equals(request.headers().get(HDR_IF_NONE_MATCH))) {
            return ServeResult.notModified(etag);
        }

        long size = Files.size(file);
        return ServeResult.ok(contentTypeFor(file), file, size, etag);
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

    public record ServeResult(HttpStatus status, String contentType, Path filePath, long size, String etag) {
        static ServeResult ok(String contentType, Path filePath, long size, String etag) {
            return new ServeResult(HttpStatus.OK, contentType, filePath, size, etag);
        }
        static ServeResult notModified(String etag) {
            return new ServeResult(HttpStatus.NOT_MODIFIED, null, null, 0, etag);
        }
    }

    static class ForbiddenException extends RuntimeException {}
    static class NotFoundException extends RuntimeException {}
}