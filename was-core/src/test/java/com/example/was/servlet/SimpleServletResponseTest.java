package com.example.was.servlet;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class SimpleServletResponseTest {

    @Test
    public void writerAccumulatesOutput() throws IOException {
        SimpleServletResponse res = new SimpleServletResponse();
        res.getWriter().write("Hello, ");
        res.getWriter().write("World");
        assertEquals("Hello, World", res.getBody());
    }

    @Test
    public void defaultContentTypeIsHtml() {
        SimpleServletResponse res = new SimpleServletResponse();
        assertEquals("text/html; charset=UTF-8", res.getContentType());
    }

    @Test
    public void setContentTypeOverridesDefault() {
        SimpleServletResponse res = new SimpleServletResponse();
        res.setContentType("application/json");
        assertEquals("application/json", res.getContentType());
    }

    @Test
    public void emptyBodyBeforeWrite() {
        SimpleServletResponse res = new SimpleServletResponse();
        assertEquals("", res.getBody());
    }
}
