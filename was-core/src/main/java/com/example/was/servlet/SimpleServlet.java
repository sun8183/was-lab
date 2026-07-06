package com.example.was.servlet;

import java.io.IOException;

public interface SimpleServlet {

    default void init() {}

    void service(ServletRequest req, ServletResponse res) throws IOException;

    default void destroy() {}
}
