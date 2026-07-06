package com.example.was.servlet;

import java.io.Writer;

public interface ServletResponse {

    Writer getWriter();

    void setContentType(String contentType);
}
