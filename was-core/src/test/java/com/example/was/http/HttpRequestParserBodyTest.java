package com.example.was.http;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class HttpRequestParserBodyTest {

    private final HttpRequestParser parser = new HttpRequestParser();

    private static InputStream stream(String raw) {
        return new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static int utf8Length(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    private HttpStatus parseFailureStatus(String raw) throws IOException {
        try {
            parser.parse(stream(raw));
            fail("expected HttpParseException");
            return null;
        } catch (HttpParseException e) {
            return e.status();
        }
    }

    @Test
    public void readsBodyByContentLengthBytesNotChars() throws IOException {
        String body = "name=김철수";
        HttpRequest req = parser.parse(stream(
                "POST /Hello HTTP/1.1\r\nContent-Length: " + utf8Length(body) + "\r\n\r\n" + body));

        assertEquals(body, new String(req.body(), StandardCharsets.UTF_8));
    }

    @Test
    public void bodyDoesNotLeakIntoNextRequestOnSameConnection() throws IOException {
        String body = "name=김철수";
        InputStream in = stream(
                "GET /a HTTP/1.1\r\nContent-Length: " + utf8Length(body) + "\r\n\r\n" + body
                        + "GET /b HTTP/1.1\r\nHost: a.com\r\n\r\n");

        HttpRequest first = parser.parse(in);
        HttpRequest second = parser.parse(in);

        assertEquals("/a", first.path());
        assertEquals("GET", second.method());
        assertEquals("/b", second.path());
        assertEquals("a.com", second.headers().get("Host"));
    }

    @Test
    public void noContentLengthMeansEmptyBody() throws IOException {
        HttpRequest req = parser.parse(stream("GET / HTTP/1.1\r\nHost: a.com\r\n\r\n"));
        assertEquals(0, req.body().length);
    }

    @Test
    public void headerNamesAreCaseInsensitive() throws IOException {
        HttpRequest req = parser.parse(stream("GET / HTTP/1.1\r\nhost: a.com\r\ncontent-length: 2\r\n\r\nhi"));
        assertEquals("a.com", req.headers().get("Host"));
        assertEquals("hi", new String(req.body(), StandardCharsets.UTF_8));
    }

    @Test
    public void bareLfLineTerminatorAccepted() throws IOException {
        HttpRequest req = parser.parse(stream("GET / HTTP/1.1\nHost: a.com\n\n"));
        assertEquals("a.com", req.headers().get("Host"));
    }

    @Test
    public void contentLengthOverLimitIs413() throws IOException {
        assertEquals(HttpStatus.CONTENT_TOO_LARGE, parseFailureStatus(
                "POST / HTTP/1.1\r\nContent-Length: " + (HttpRequestParser.DEFAULT_MAX_BODY_BYTES + 1) + "\r\n\r\n"));
    }

    @Test
    public void configuredBodyLimitIsApplied() throws IOException {
        HttpRequestParser smallLimitParser = new HttpRequestParser(5);

        HttpRequest ok = smallLimitParser.parse(stream("POST / HTTP/1.1\r\nContent-Length: 5\r\n\r\nabcde"));
        assertEquals("abcde", new String(ok.body(), StandardCharsets.UTF_8));

        try {
            smallLimitParser.parse(stream("POST / HTTP/1.1\r\nContent-Length: 6\r\n\r\nabcdef"));
            fail("expected HttpParseException");
        } catch (HttpParseException e) {
            assertEquals(HttpStatus.CONTENT_TOO_LARGE, e.status());
        }
    }

    @Test
    public void hugeContentLengthDoesNotOverflow() throws IOException {
        assertEquals(HttpStatus.CONTENT_TOO_LARGE, parseFailureStatus(
                "POST / HTTP/1.1\r\nContent-Length: 99999999999999999999999\r\n\r\n"));
    }

    @Test
    public void nonNumericContentLengthIs400() throws IOException {
        assertEquals(HttpStatus.BAD_REQUEST, parseFailureStatus("POST / HTTP/1.1\r\nContent-Length: abc\r\n\r\n"));
        assertEquals(HttpStatus.BAD_REQUEST, parseFailureStatus("POST / HTTP/1.1\r\nContent-Length: -1\r\n\r\n"));
    }

    @Test
    public void conflictingContentLengthIs400() throws IOException {
        assertEquals(HttpStatus.BAD_REQUEST, parseFailureStatus(
                "POST / HTTP/1.1\r\nContent-Length: 3\r\nContent-Length: 5\r\n\r\nabcde"));
    }

    @Test
    public void duplicateIdenticalContentLengthAccepted() throws IOException {
        HttpRequest req = parser.parse(stream("POST / HTTP/1.1\r\nContent-Length: 3\r\nContent-Length: 3\r\n\r\nabc"));
        assertEquals("abc", new String(req.body(), StandardCharsets.UTF_8));
    }

    @Test
    public void contentLengthWithTransferEncodingIs400() throws IOException {
        assertEquals(HttpStatus.BAD_REQUEST, parseFailureStatus(
                "POST / HTTP/1.1\r\nContent-Length: 3\r\nTransfer-Encoding: chunked\r\n\r\nabc"));
    }

    @Test
    public void transferEncodingIs501() throws IOException {
        assertEquals(HttpStatus.NOT_IMPLEMENTED, parseFailureStatus(
                "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"));
    }

    @Test
    public void lineTooLongIs400() throws IOException {
        String longPath = "/" + "a".repeat(HttpRequestParser.MAX_LINE_BYTES);
        assertEquals(HttpStatus.BAD_REQUEST, parseFailureStatus("GET " + longPath + " HTTP/1.1\r\n\r\n"));
    }

    @Test
    public void tooManyHeadersIs400() throws IOException {
        StringBuilder sb = new StringBuilder("GET / HTTP/1.1\r\n");
        for (int i = 0; i <= HttpRequestParser.MAX_HEADER_COUNT; i++) {
            sb.append("X-H").append(i).append(": v\r\n");
        }
        sb.append("\r\n");
        assertEquals(HttpStatus.BAD_REQUEST, parseFailureStatus(sb.toString()));
    }

    @Test
    public void whitespaceBeforeColonIs400() throws IOException {
        assertEquals(HttpStatus.BAD_REQUEST, parseFailureStatus("GET / HTTP/1.1\r\nHost : a.com\r\n\r\n"));
    }

    @Test
    public void obsFoldLineIs400() throws IOException {
        assertEquals(HttpStatus.BAD_REQUEST, parseFailureStatus("GET / HTTP/1.1\r\nX-A: a\r\n b: c\r\n\r\n"));
        assertEquals(HttpStatus.BAD_REQUEST, parseFailureStatus("GET / HTTP/1.1\r\nX-A: a\r\n continued\r\n\r\n"));
    }

    @Test
    public void bareCrInLineIs400() throws IOException {
        assertEquals(HttpStatus.BAD_REQUEST, parseFailureStatus("GET / HTTP/1.1\r\nX-A: a\rb\r\n\r\n"));
    }

    @Test
    public void binaryBodyPreservedExactly() throws IOException {
        byte[] body = {(byte) 0xFF, (byte) 0xFE, 0x00, (byte) 0x80};
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.write("POST / HTTP/1.1\r\nContent-Length: 4\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        raw.write(body);

        HttpRequest req = parser.parse(new ByteArrayInputStream(raw.toByteArray()));
        assertArrayEquals(body, req.body());
    }

    @Test(expected = IOException.class)
    public void truncatedBodyThrowsIOException() throws IOException {
        parser.parse(stream("POST / HTTP/1.1\r\nContent-Length: 10\r\n\r\nabc"));
    }

    @Test(expected = IOException.class)
    public void emptyStreamThrowsIOException() throws IOException {
        parser.parse(stream(""));
    }
}