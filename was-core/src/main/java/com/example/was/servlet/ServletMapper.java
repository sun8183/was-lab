package com.example.was.servlet;

import java.util.Optional;

public interface ServletMapper {

    /**
     * @return 경로에 매핑된 서블릿. 서블릿 경로가 아니면 empty(정적 파일로 처리된다).
     * @throws ServletInitException 서블릿 클래스는 있지만 생성/초기화에 실패한 경우(500 으로 응답된다)
     */
    Optional<SimpleServlet> resolve(String requestPath);

    default void destroyAll() {}
}
