package service;

import com.example.was.servlet.ServletRequest;
import com.example.was.servlet.ServletResponse;
import com.example.was.servlet.SimpleServlet;

import java.io.IOException;
import java.io.Writer;

public class Hello implements SimpleServlet {

    @Override
    public void service(ServletRequest req, ServletResponse res) throws IOException {
        Writer writer = res.getWriter();
        writer.write("Hello from service package, ");
        writer.write(req.getParameter("name"));
    }
}
