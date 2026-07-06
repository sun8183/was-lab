package service;

import com.example.was.servlet.*;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.*;

public class ServiceHelloServletTest {

    private final DirectClassServletMapper mapper = new DirectClassServletMapper();

    @Test
    public void urlMapsToServiceHelloClass() {
        Optional<SimpleServlet> result = mapper.resolve("/service.Hello");
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof Hello);
    }

    @Test
    public void serviceHelloServletWritesNameParam() throws IOException {
        SimpleServletRequest req = new SimpleServletRequest("GET", "/service.Hello?name=Bob");
        SimpleServletResponse res = new SimpleServletResponse();

        new Hello().service(req, res);

        assertTrue(res.getBody().contains("Bob"));
    }

    @Test
    public void serviceHelloViaMapper() throws IOException {
        SimpleServlet servlet = mapper.resolve("/service.Hello").orElseThrow();
        SimpleServletRequest req = new SimpleServletRequest("GET", "/service.Hello?name=Carol");
        SimpleServletResponse res = new SimpleServletResponse();

        servlet.service(req, res);

        assertTrue(res.getBody().contains("Carol"));
    }

    @Test
    public void helloAndServiceHelloAreDifferentClasses() {
        SimpleServlet hello = mapper.resolve("/Hello").orElseThrow();
        SimpleServlet serviceHello = mapper.resolve("/service.Hello").orElseThrow();
        assertNotEquals(hello.getClass(), serviceHello.getClass());
    }
}
