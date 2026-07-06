package com.example.was;

import com.example.was.config.ServerConfig;
import com.example.was.config.VirtualHostConfig;
import com.example.was.http.HttpRequest;
import com.example.was.http.HttpStatus;
import com.example.was.servlet.ServletMapper;
import com.example.was.servlet.SimpleServlet;
import com.example.was.servlet.SimpleServletRequest;
import com.example.was.servlet.SimpleServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class RequestDispatcher {

    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String HDR_HOST = "Host";

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

    public HttpStatus dispatch(OutputStream out, HttpRequest request, boolean keepAlive) throws IOException {
        VirtualHostConfig vhost = config.resolveVirtualHost(request.headers().get(HDR_HOST));
        Optional<SimpleServlet> servlet = servletMapper.resolve(request.path());

        try {
            return servlet.isPresent()
                    ? dispatchServlet(out, servlet.get(), request, keepAlive)
                    : serveStaticFile(out, vhost, request, keepAlive);
        } catch (StaticFileHandler.ForbiddenException e) {
            responseWriter.writeErrorResponse(out, vhost, HttpStatus.FORBIDDEN, keepAlive);
            return HttpStatus.FORBIDDEN;
        } catch (StaticFileHandler.NotFoundException e) {
            responseWriter.writeErrorResponse(out, vhost, HttpStatus.NOT_FOUND, keepAlive);
            return HttpStatus.NOT_FOUND;
        }
    }

    private HttpStatus serveStaticFile(OutputStream out, VirtualHostConfig vhost, HttpRequest request, boolean keepAlive) throws IOException {
        StaticFileHandler.ServeResult result = staticFileHandler.resolve(vhost, request);
        if (result.status() == HttpStatus.NOT_MODIFIED) {
            responseWriter.writeNotModified(out, result.etag(), keepAlive);
        } else {
            responseWriter.writeResponse(out, HttpStatus.OK, result.contentType(), result.body(), keepAlive, result.etag());
        }
        return result.status();
    }

    private HttpStatus dispatchServlet(OutputStream out, SimpleServlet servlet, HttpRequest request, boolean keepAlive) throws IOException {
        SimpleServletRequest servletRequest = new SimpleServletRequest(request.method(), request.path());
        SimpleServletResponse servletResponse = new SimpleServletResponse();
        servlet.service(servletRequest, servletResponse);
        byte[] body = servletResponse.getBody().getBytes(CHARSET);
        responseWriter.writeResponse(out, HttpStatus.OK, servletResponse.getContentType(), body, keepAlive, null);
        return HttpStatus.OK;
    }
}