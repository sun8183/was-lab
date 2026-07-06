package com.example.was.servlet;

import org.junit.Test;

import static org.junit.Assert.*;

public class SimpleServletRequestTest {

    @Test
    public void singleQueryParam() {
        SimpleServletRequest req = new SimpleServletRequest("GET", "/Hello?name=World");
        assertEquals("World", req.getParameter("name"));
    }

    @Test
    public void multipleQueryParams() {
        SimpleServletRequest req = new SimpleServletRequest("GET", "/Hello?name=Alice&age=30");
        assertEquals("Alice", req.getParameter("name"));
        assertEquals("30", req.getParameter("age"));
    }

    @Test
    public void noQueryParamReturnsNull() {
        SimpleServletRequest req = new SimpleServletRequest("GET", "/Hello");
        assertNull(req.getParameter("name"));
    }

    @Test
    public void urlEncodedParam() {
        SimpleServletRequest req = new SimpleServletRequest("GET", "/Hello?name=Hello+World");
        assertEquals("Hello World", req.getParameter("name"));
    }

    @Test
    public void paramWithoutValue() {
        SimpleServletRequest req = new SimpleServletRequest("GET", "/Hello?flag");
        assertEquals("", req.getParameter("flag"));
    }

    @Test
    public void missingParamKeyReturnsNull() {
        SimpleServletRequest req = new SimpleServletRequest("GET", "/Hello?name=Alice");
        assertNull(req.getParameter("age"));
    }

    @Test
    public void getMethod() {
        SimpleServletRequest req = new SimpleServletRequest("POST", "/Hello");
        assertEquals("POST", req.getMethod());
    }
}
