package com.example.was.http;

/**
 * 요청을 파싱할 수 없을 때 클라이언트에 돌려줄 상태 코드를 함께 전달한다.
 * 이 예외가 나면 바디 경계를 신뢰할 수 없으므로 응답 후 연결을 닫아야 한다.
 */
public class HttpParseException extends RuntimeException {

    private final HttpStatus status;

    public HttpParseException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static HttpParseException badRequest(String message) {
        return new HttpParseException(HttpStatus.BAD_REQUEST, message);
    }

    public HttpStatus status() {
        return status;
    }
}