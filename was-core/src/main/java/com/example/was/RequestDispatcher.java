package com.example.was;

import com.example.was.config.ServerConfig;
import com.example.was.config.VirtualHostConfig;
import com.example.was.http.HttpMethod;
import com.example.was.http.HttpRequest;
import com.example.was.http.HttpStatus;
import com.example.was.servlet.ServletInitException;
import com.example.was.servlet.ServletMapper;
import com.example.was.servlet.SimpleServlet;
import com.example.was.servlet.SimpleServletRequest;
import com.example.was.servlet.SimpleServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class RequestDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RequestDispatcher.class);
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String HDR_HOST = "Host";

    // 정적 파일 허용 메서드는 여기 한 곳에서만 정의한다. 실제 검사와 405 의 Allow 헤더 값이 모두 이 집합에서 나온다.
    private static final Set<HttpMethod> STATIC_METHODS = EnumSet.of(HttpMethod.GET, HttpMethod.HEAD);
    private static final String STATIC_ALLOW_HEADER = STATIC_METHODS.stream()
            .map(HttpMethod::name)
            .collect(Collectors.joining(", "));

    private final ServerConfig config;
    private final ServletMapper servletMapper;
    private final StaticFileHandler staticFileHandler;
    private final HttpResponseWriter responseWriter;

    public RequestDispatcher(ServerConfig config, ServletMapper servletMapper,
                             StaticFileHandler staticFileHandler, HttpResponseWriter responseWriter) {
        this.config = config;
        this.servletMapper = servletMapper;
        this.staticFileHandler = staticFileHandler;
        this.responseWriter = responseWriter;
    }

    public HttpStatus dispatch(OutputStream socketOut, HttpRequest request, boolean keepAlive) throws IOException {
        // HEAD 는 GET 과 같은 응답에서 바디만 뺀다. 서블릿/에러 페이지 등 어떤 경로든 바디가 나가지 않도록 스트림 단에서 막는다.
        OutputStream out = isHead(request) ? new HeadResponseOutputStream(socketOut) : socketOut;
        VirtualHostConfig vhost = config.resolveVirtualHost(request.headers().get(HDR_HOST));

        try {
            Optional<SimpleServlet> servlet = servletMapper.resolve(request.path());
            return servlet.isPresent()
                    ? dispatchServlet(out, servlet.get(), request, keepAlive)
                    : serveStaticFile(out, vhost, request, keepAlive);
        } catch (ServletInitException e) {
            // 서블릿은 존재하지만 생성에 실패한 서버 측 문제이므로 404 가 아닌 500. 원인은 매퍼에서 이미 로그로 남겼다.
            responseWriter.writeErrorResponse(out, vhost, HttpStatus.INTERNAL_SERVER_ERROR, keepAlive);
            return HttpStatus.INTERNAL_SERVER_ERROR;
        } catch (StaticFileHandler.ForbiddenException e) {
            responseWriter.writeErrorResponse(out, vhost, HttpStatus.FORBIDDEN, keepAlive);
            return HttpStatus.FORBIDDEN;
        } catch (StaticFileHandler.NotFoundException e) {
            responseWriter.writeErrorResponse(out, vhost, HttpStatus.NOT_FOUND, keepAlive);
            return HttpStatus.NOT_FOUND;
        } catch (RuntimeException e) {
            log.error("unhandled exception while dispatching {} {}", request.method(), request.path(), e);
            responseWriter.writeErrorResponse(out, vhost, HttpStatus.INTERNAL_SERVER_ERROR, keepAlive);
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private HttpStatus serveStaticFile(OutputStream out, VirtualHostConfig vhost, HttpRequest request, boolean keepAlive) throws IOException {
        // 순서: 403/404(리소스 확인) → 405(메서드) → 304/200.
        // 405 는 "리소스는 있지만 메서드를 지원하지 않음"이므로, 없는 경로에는 404 가 먼저 나가야 한다.
        Path file = staticFileHandler.locate(vhost, request);

        // 정적 파일은 조회(GET, HEAD)만 가능하다. POST 등은 서블릿에서만 처리한다.
        if (STATIC_METHODS.stream().noneMatch(m -> m.name().equals(request.method()))) {
            responseWriter.writeMethodNotAllowed(out, STATIC_ALLOW_HEADER, keepAlive);
            return HttpStatus.METHOD_NOT_ALLOWED;
        }
        StaticFileHandler.ServeResult result = staticFileHandler.serve(file, request);
        if (result.status() == HttpStatus.NOT_MODIFIED) {
            responseWriter.writeNotModified(out, result.etag(), keepAlive);
        } else if (isHead(request)) {
            // 스트림이 바디를 버리더라도 파일을 끝까지 읽는 비용이 들므로, 아예 헤더만 쓴다.
            responseWriter.writeHeadersOnly(out, HttpStatus.OK, result.contentType(), result.size(), keepAlive, result.etag());
        } else {
            responseWriter.writeResponse(out, HttpStatus.OK, result.contentType(), result.filePath(), result.size(), keepAlive, result.etag());
        }
        return result.status();
    }

    private static boolean isHead(HttpRequest request) {
        return HttpMethod.HEAD.name().equals(request.method());
    }

    private HttpStatus dispatchServlet(OutputStream out, SimpleServlet servlet, HttpRequest request, boolean keepAlive) throws IOException {
        SimpleServletRequest servletRequest = new SimpleServletRequest(
                request.method(), request.path(), request.headers(), request.body());
        SimpleServletResponse servletResponse = new SimpleServletResponse();
        servlet.service(servletRequest, servletResponse);
        byte[] body = servletResponse.getBody().getBytes(CHARSET);
        responseWriter.writeResponse(out, HttpStatus.OK, servletResponse.getContentType(), body, keepAlive, null);
        return HttpStatus.OK;
    }
}