import com.example.was.servlet.ServletRequest;
import com.example.was.servlet.ServletResponse;
import com.example.was.servlet.SimpleServlet;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CurrentTime implements SimpleServlet {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void service(ServletRequest req, ServletResponse res) throws IOException {
        Writer writer = res.getWriter();
        writer.write(LocalDateTime.now().format(FORMATTER));
    }
}
