package com.example.was.http;

public enum HttpMethod {
    GET,
    HEAD,
    POST;

    // 메서드 이름은 대소문자를 구분한다(RFC 9110 9.1). "get" 은 GET 이 아니다.
    public static boolean isSupported(String method) {
        for (HttpMethod m : values()) {
            if (m.name().equals(method)) return true;
        }
        return false;
    }
}
