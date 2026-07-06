package com.example.was;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.Assert.*;

public class StackTraceLoggingTest {

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @Before
    public void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ConnectionHandler.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @After
    public void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    public void errorWithExceptionCapturesThrowableProxy() {
        IOException cause = new IOException("disk read failure");

        logger.error("500 internal server error", cause);

        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        assertNotNull("throwable proxy must exist", event.getThrowableProxy());
        assertEquals(IOException.class.getName(), event.getThrowableProxy().getClassName());
        assertEquals("disk read failure", event.getThrowableProxy().getMessage());
    }

    @Test
    public void stackTraceFramesPresent() {
        RuntimeException ex = new RuntimeException("unexpected");

        logger.error("unhandled error", ex);

        IThrowableProxy proxy = listAppender.list.get(0).getThrowableProxy();
        assertTrue("stack trace must have frames",
                proxy.getStackTraceElementProxyArray().length > 0);
    }

    @Test
    public void causeChainCaptured() {
        IOException root = new IOException("root cause");
        RuntimeException wrapper = new RuntimeException("wrapper", root);

        logger.error("chained error", wrapper);

        IThrowableProxy proxy = listAppender.list.get(0).getThrowableProxy();
        assertNotNull("cause chain must be captured", proxy.getCause());
        assertEquals(IOException.class.getName(), proxy.getCause().getClassName());
    }
}
