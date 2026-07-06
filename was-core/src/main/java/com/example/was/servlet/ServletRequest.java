package com.example.was.servlet;

public interface ServletRequest {

    String getMethod();

    String getParameter(String name);
}
