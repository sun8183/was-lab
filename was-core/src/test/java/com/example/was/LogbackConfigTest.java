package com.example.was;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class LogbackConfigTest {

    private static RollingFileAppender<?> fileAppender;
    private static SizeAndTimeBasedRollingPolicy<?> rollingPolicy;
    private static Logger rootLogger;

    @BeforeClass
    public static void setUp() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        fileAppender = (RollingFileAppender<?>) rootLogger.getAppender("FILE");
        rollingPolicy = (SizeAndTimeBasedRollingPolicy<?>) fileAppender.getRollingPolicy();
    }

    @Test
    public void fileAppenderConfigured() {
        assertNotNull("FILE appender must exist", fileAppender);
    }

    @Test
    public void dailyRollingPolicyConfigured() {
        String pattern = rollingPolicy.getFileNamePattern();
        assertTrue("fileNamePattern must contain yyyy-MM-dd for daily rolling",
                pattern.contains("yyyy-MM-dd"));
    }

    @Test
    public void maxHistorySet() {
        assertTrue("maxHistory must be positive", rollingPolicy.getMaxHistory() > 0);
    }

    @Test
    public void rootLogLevelIsInfo() {
        assertEquals(Level.INFO, rootLogger.getLevel());
    }
}
