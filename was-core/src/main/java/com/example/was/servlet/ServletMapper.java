package com.example.was.servlet;

import java.util.Optional;

public interface ServletMapper {

    Optional<SimpleServlet> resolve(String requestPath);

    default void destroyAll() {}
}
