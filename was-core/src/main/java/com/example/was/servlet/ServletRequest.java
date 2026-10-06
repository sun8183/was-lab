package com.example.was.servlet;

public interface ServletRequest {

    String getMethod();

    String getParameter(String name);

    /** 헤더 이름은 대소문자를 구분하지 않는다. 없으면 null. */
    String getHeader(String name);

    /** 요청 바디 원본 바이트. 바디가 없으면 길이 0 배열. */
    byte[] getBody();
}