package com.example.was.servlet;

/**
 * 서블릿 클래스는 맞지만 생성/초기화(static 초기화, 생성자, init())에 실패했을 때 던진다.
 * "서블릿이 아님"(Optional.empty → 정적 파일 처리)과 구분해 500 으로 응답하기 위함.
 */
public class ServletInitException extends RuntimeException {

    public ServletInitException(String className, Throwable cause) {
        super("Cannot create servlet " + className, cause);
    }
}