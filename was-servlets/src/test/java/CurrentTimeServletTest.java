import com.example.was.servlet.*;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.Assert.*;

public class CurrentTimeServletTest {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DirectClassServletMapper mapper = new DirectClassServletMapper();

    @Test
    public void urlMapsToCurrentTimeClass() {
        Optional<SimpleServlet> result = mapper.resolve("/CurrentTime");
        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof CurrentTime);
    }

    @Test
    public void outputMatchesExpectedFormat() throws IOException {
        SimpleServletResponse res = new SimpleServletResponse();
        new CurrentTime().service(new SimpleServletRequest("GET", "/CurrentTime"), res);

        String body = res.getBody();
        LocalDateTime parsed = LocalDateTime.parse(body, FORMATTER);
        assertNotNull(parsed);
    }

    @Test
    public void outputIsCurrentTime() throws IOException {
        LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        SimpleServletResponse res = new SimpleServletResponse();
        new CurrentTime().service(new SimpleServletRequest("GET", "/CurrentTime"), res);
        LocalDateTime after = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        LocalDateTime output = LocalDateTime.parse(res.getBody(), FORMATTER);
        assertFalse("출력 시각이 현재보다 이전", output.isBefore(before));
        assertFalse("출력 시각이 현재보다 이후", output.isAfter(after));
    }

    @Test
    public void eachCallReturnsCurrentTime() throws IOException {
        SimpleServletResponse res1 = new SimpleServletResponse();
        new CurrentTime().service(new SimpleServletRequest("GET", "/CurrentTime"), res1);

        SimpleServletResponse res2 = new SimpleServletResponse();
        new CurrentTime().service(new SimpleServletRequest("GET", "/CurrentTime"), res2);

        LocalDateTime t1 = LocalDateTime.parse(res1.getBody(), FORMATTER);
        LocalDateTime t2 = LocalDateTime.parse(res2.getBody(), FORMATTER);
        assertFalse("두 번째 호출 시각이 첫 번째보다 이전일 수 없음", t2.isBefore(t1));
    }

    @Test
    public void defaultContentTypeIsHtml() throws IOException {
        SimpleServletResponse res = new SimpleServletResponse();
        new CurrentTime().service(new SimpleServletRequest("GET", "/CurrentTime"), res);
        assertEquals("text/html; charset=UTF-8", res.getContentType());
    }
}
