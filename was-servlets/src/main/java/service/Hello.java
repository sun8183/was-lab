package service;

import com.example.was.servlet.ServletRequest;
import com.example.was.servlet.ServletResponse;
import com.example.was.servlet.SimpleServlet;

import java.io.IOException;
import java.io.Writer;

public class Hello implements SimpleServlet {

    private static final String DEFAULT_NAME = "World";

    @Override
    public void service(ServletRequest req, ServletResponse res) throws IOException {
        // name 파라미터가 없으면 Writer.write(null) 이 NPE 를 던져 500 이 되므로 기본값을 쓴다.
        String name = req.getParameter("name");
        Writer writer = res.getWriter();
        writer.write("Hello from service package, ");
        writer.write(name != null ? name : DEFAULT_NAME);
    }
}
