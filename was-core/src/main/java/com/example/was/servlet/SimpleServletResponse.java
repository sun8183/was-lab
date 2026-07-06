package com.example.was.servlet;

import java.io.StringWriter;
import java.io.Writer;

public class SimpleServletResponse implements ServletResponse {

    private final StringWriter buffer = new StringWriter();
    private String contentType = "text/html; charset=UTF-8";

    @Override
    public Writer getWriter() {
        return buffer;
    }

    @Override
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    public String getBody() {
        return buffer.toString();
    }
}
