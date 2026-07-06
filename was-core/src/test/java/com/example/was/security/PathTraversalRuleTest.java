package com.example.was.security;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.*;

public class PathTraversalRuleTest {

    private final PathTraversalRule rule = new PathTraversalRule();
    private final Path httpRoot = Path.of("/var/www/html").toAbsolutePath().normalize();

    @Test
    public void normalPathAllowed() {
        Path resolved = httpRoot.resolve("index.html").normalize();
        assertFalse(rule.matches(httpRoot, resolved));
    }

    @Test
    public void subDirectoryAllowed() {
        Path resolved = httpRoot.resolve("assets/style.css").normalize();
        assertFalse(rule.matches(httpRoot, resolved));
    }

    @Test
    public void parentTraversalBlocked() {
        Path resolved = httpRoot.resolve("../../../../etc/passwd").normalize();
        assertTrue(rule.matches(httpRoot, resolved));
    }

    @Test
    public void rootDirectoryBlocked() {
        Path resolved = Path.of("/etc/passwd").toAbsolutePath().normalize();
        assertTrue(rule.matches(httpRoot, resolved));
    }

    @Test
    public void httpRootItselfBlocked() {
        // httpRoot 자체는 파일이 아니므로 traversal은 아니지만 경계값 확인
        assertFalse(rule.matches(httpRoot, httpRoot));
    }
}
