import com.example.was.servlet.*;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.*;

public class HelloServletTest {

    private final DirectClassServletMapper mapper = new DirectClassServletMapper();

    @Test
    public void urlMapsToHelloClass() {
        Optional<SimpleServlet> result = mapper.resolve("/Hello");
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof Hello);
    }

    @Test
    public void helloServletWritesNameParam() throws IOException {
        SimpleServletRequest req = new SimpleServletRequest("GET", "/Hello?name=World");
        SimpleServletResponse res = new SimpleServletResponse();

        new Hello().service(req, res);

        assertEquals("Hello, World", res.getBody());
    }

    @Test
    public void helloServletViaMapper() throws IOException {
        SimpleServlet servlet = mapper.resolve("/Hello").orElseThrow();
        SimpleServletRequest req = new SimpleServletRequest("GET", "/Hello?name=Alice");
        SimpleServletResponse res = new SimpleServletResponse();

        servlet.service(req, res);

        assertTrue(res.getBody().contains("Alice"));
    }

    @Test
    public void queryStringStrippedOnMapping() {
        Optional<SimpleServlet> result = mapper.resolve("/Hello?name=Alice");
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof Hello);
    }
}
