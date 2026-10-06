package com.example.was.servlet;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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

    @Test
    public void bodyAndHeadersDelivered() {
        byte[] body = "name=김철수".getBytes(StandardCharsets.UTF_8);
        SimpleServletRequest req = new SimpleServletRequest("POST", "/Hello",
                Map.of("Content-Type", "application/x-www-form-urlencoded"), body);

        assertArrayEquals(body, req.getBody());
        assertEquals("application/x-www-form-urlencoded", req.getHeader("content-type"));
    }

    private static SimpleServletRequest formRequest(String method, String path, String contentType, String body) {
        return new SimpleServletRequest(method, path, Map.of("Content-Type", contentType),
                body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void postFormBodyParsedIntoParameters() {
        SimpleServletRequest req = formRequest("POST", "/Hello",
                "application/x-www-form-urlencoded", "name=%EA%B9%80%EC%B2%A0%EC%88%98&age=30");
        assertEquals("김철수", req.getParameter("name"));
        assertEquals("30", req.getParameter("age"));
    }

    @Test
    public void queryStringAndFormBodyMergedWithQueryPriority() {
        SimpleServletRequest req = formRequest("POST", "/Hello?name=query&page=1",
                "application/x-www-form-urlencoded", "name=body&age=30");
        assertEquals("query", req.getParameter("name"));
        assertEquals("1", req.getParameter("page"));
        assertEquals("30", req.getParameter("age"));
    }

    @Test
    public void contentTypeWithCharsetAndDifferentCaseRecognized() {
        SimpleServletRequest req = formRequest("POST", "/Hello",
                "Application/X-WWW-Form-Urlencoded; charset=UTF-8", "name=kim");
        assertEquals("kim", req.getParameter("name"));
    }

    @Test
    public void formCharsetParameterApplied() {
        byte[] eucKr = "name=%B1%E8".getBytes(StandardCharsets.US_ASCII); // "김" in EUC-KR
        SimpleServletRequest req = new SimpleServletRequest("POST", "/Hello",
                Map.of("Content-Type", "application/x-www-form-urlencoded; charset=EUC-KR"), eucKr);
        assertEquals("김", req.getParameter("name"));
    }

    @Test
    public void nonFormContentTypeBodyNotParsed() {
        SimpleServletRequest req = formRequest("POST", "/Hello", "application/json", "name=kim");
        assertNull(req.getParameter("name"));
    }

    @Test
    public void getWithFormBodyNotParsed() {
        SimpleServletRequest req = formRequest("GET", "/Hello", "application/x-www-form-urlencoded", "name=kim");
        assertNull(req.getParameter("name"));
    }

    @Test
    public void malformedEncodingPairIgnored() {
        SimpleServletRequest req = formRequest("POST", "/Hello",
                "application/x-www-form-urlencoded", "bad=%zz&name=kim");
        assertNull(req.getParameter("bad"));
        assertEquals("kim", req.getParameter("name"));
    }

    @Test
    public void noBodyConstructorGivesEmptyBodyAndNoHeaders() {
        SimpleServletRequest req = new SimpleServletRequest("GET", "/Hello");
        assertEquals(0, req.getBody().length);
        assertNull(req.getHeader("Host"));
    }
}
