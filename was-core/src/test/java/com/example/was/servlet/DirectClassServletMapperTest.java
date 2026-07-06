package com.example.was.servlet;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class DirectClassServletMapperTest {

    private final DirectClassServletMapper mapper = new DirectClassServletMapper();

    @Test
    public void resolvesByClassName() {
        Optional<SimpleServlet> result = mapper.resolve("/com.example.was.servlet.GreetingServlet");
        assertTrue(result.isPresent());
        assertInstanceOf(GreetingServlet.class, result.get());
    }

    @Test
    public void queryStringStrippedBeforeResolve() {
        Optional<SimpleServlet> result = mapper.resolve("/com.example.was.servlet.GreetingServlet?name=Alice");
        assertTrue(result.isPresent());
        assertInstanceOf(GreetingServlet.class, result.get());
    }

    @Test
    public void sameInstanceReturnedOnSecondCall() {
        SimpleServlet first = mapper.resolve("/com.example.was.servlet.GreetingServlet").orElseThrow();
        SimpleServlet second = mapper.resolve("/com.example.was.servlet.GreetingServlet").orElseThrow();
        assertSame(first, second);
    }

    @Test
    public void unknownClassReturnsEmpty() {
        Optional<SimpleServlet> result = mapper.resolve("/com.example.NonExistent");
        assertFalse(result.isPresent());
    }

    @Test
    public void nonServletClassReturnsEmpty() {
        Optional<SimpleServlet> result = mapper.resolve("/java.lang.String");
        assertFalse(result.isPresent());
    }

    @Test
    public void rootPathReturnsEmpty() {
        Optional<SimpleServlet> result = mapper.resolve("/");
        assertFalse(result.isPresent());
    }

    private static <T> void assertInstanceOf(Class<T> expected, Object actual) {
        assertTrue("Expected instance of " + expected.getName() + " but was " + actual.getClass().getName(),
                expected.isInstance(actual));
    }
}
