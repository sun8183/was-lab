package com.example.was.security;

import org.junit.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.*;

public class BlockedExtensionRuleTest {

    private final Path httpRoot = Path.of("/var/www/html").toAbsolutePath().normalize();

    private BlockedExtensionRule rule(String... extensions) {
        return new BlockedExtensionRule(Set.of(extensions));
    }

    @Test
    public void exeFileBlocked() {
        assertTrue(rule(".exe").matches(httpRoot, httpRoot.resolve("malware.exe")));
    }

    @Test
    public void exeUpperCaseBlocked() {
        assertTrue(rule(".exe").matches(httpRoot, httpRoot.resolve("MALWARE.EXE")));
    }

    @Test
    public void htmlFileAllowed() {
        assertFalse(rule(".exe").matches(httpRoot, httpRoot.resolve("index.html")));
    }

    @Test
    public void multipleBlockedExtensions() {
        BlockedExtensionRule r = rule(".exe", ".bat", ".sh");
        assertTrue(r.matches(httpRoot, httpRoot.resolve("run.bat")));
        assertTrue(r.matches(httpRoot, httpRoot.resolve("run.sh")));
        assertFalse(r.matches(httpRoot, httpRoot.resolve("style.css")));
    }

    @Test
    public void noExtensionAllowed() {
        assertFalse(rule(".exe").matches(httpRoot, httpRoot.resolve("README")));
    }

    @Test
    public void partialExtensionNotBlocked() {
        assertFalse(rule(".exe").matches(httpRoot, httpRoot.resolve("file.execute")));
    }
}
