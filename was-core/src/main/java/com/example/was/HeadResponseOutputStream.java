package com.example.was;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * HEAD 요청 응답용 스트림. 헤더 끝(빈 줄, CRLF CRLF)까지만 내보내고 이후 바디 바이트는 버린다.
 * 응답을 쓰는 쪽(서블릿, 에러 페이지 등)이 HEAD 를 신경 쓰지 않아도 바디가 나가지 않는다.
 */
class HeadResponseOutputStream extends FilterOutputStream {

    private static final byte[] HEADER_END = {'\r', '\n', '\r', '\n'};

    private int matched = 0;
    private boolean headersDone = false;

    HeadResponseOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        if (headersDone) {
            return;
        }
        out.write(b);
        advance((byte) b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        int i = off;
        int end = off + len;
        while (i < end && !headersDone) {
            advance(b[i]);
            i++;
        }
        out.write(b, off, i - off);
    }

    // 소켓 스트림은 ConnectionHandler 가 관리하므로 닫지 않고 flush 만 한다.
    @Override
    public void close() throws IOException {
        flush();
    }

    private void advance(byte b) {
        if (b == HEADER_END[matched]) {
            matched++;
        } else {
            matched = (b == '\r') ? 1 : 0;
        }
        if (matched == HEADER_END.length) {
            headersDone = true;
        }
    }
}