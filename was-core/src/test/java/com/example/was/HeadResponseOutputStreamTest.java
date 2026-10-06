package com.example.was;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class HeadResponseOutputStreamTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    public void dropsBodyAfterHeaderEnd() throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        HeadResponseOutputStream out = new HeadResponseOutputStream(sink);

        out.write(bytes("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello"));

        assertEquals("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\n", sink.toString(StandardCharsets.ISO_8859_1));
    }

    @Test
    public void headerEndSplitAcrossWrites() throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        HeadResponseOutputStream out = new HeadResponseOutputStream(sink);

        out.write(bytes("HTTP/1.1 200 OK\r\n\r"));
        out.write('\n');
        out.write(bytes("body"));
        out.write('x');

        assertEquals("HTTP/1.1 200 OK\r\n\r\n", sink.toString(StandardCharsets.ISO_8859_1));
    }

    @Test
    public void closeDoesNotCloseUnderlyingStream() throws IOException {
        boolean[] closed = {false};
        ByteArrayOutputStream sink = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };

        new HeadResponseOutputStream(sink).close();

        assertFalse(closed[0]);
    }
}