package com.example.was.servlet;

import java.io.IOException;

public class GreetingServlet implements SimpleServlet {

    @Override
    public void service(ServletRequest req, ServletResponse res) throws IOException {
        res.getWriter().write("Greetings, " + req.getParameter("name"));
    }
}
