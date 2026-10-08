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
    public void wrongCaseClassNameReturnsEmptyNotServletInitException() {
        // Windows/macOS 에서는 greetingServlet.class 요청이 GreetingServlet.class 를 찾아 NoClassDefFoundError(wrong name) 가 나고,
        // Linux 에서는 ClassNotFoundException 이 난다. 어느 쪽이든 "그 이름의 서블릿 없음"(empty → 404) 이어야 한다.
        Optional<SimpleServlet> result = mapper.resolve("/com.example.was.servlet.greetingServlet");
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

    @Test
    public void nonServletClassIsNotInitialized() {
        Optional<SimpleServlet> result = mapper.resolve("/com.example.was.servlet.DirectClassServletMapperTest$StaticInitTrap");
        assertFalse(result.isPresent());
        assertFalse("non-servlet static initializer must not run", trapInitialized);
    }

    @Test
    public void servletStaticInitFailureThrowsServletInitExceptionInsteadOfError() {
        try {
            mapper.resolve("/com.example.was.servlet.DirectClassServletMapperTest$BrokenStaticInitServlet");
            fail("expected ServletInitException");
        } catch (ServletInitException e) {
            assertTrue(e.getCause() instanceof ExceptionInInitializerError);
        }
    }

    @Test
    public void initFailureThrowsServletInitExceptionAndIsNotCached() {
        String path = "/com.example.was.servlet.DirectClassServletMapperTest$BrokenInitServlet";
        assertThrows(ServletInitException.class, () -> mapper.resolve(path));
        // 실패는 캐시되지 않으므로 다음 요청에서도 다시 생성을 시도한다(empty 로 바뀌지 않는다).
        assertThrows(ServletInitException.class, () -> mapper.resolve(path));
    }

    public static class BrokenInitServlet implements SimpleServlet {
        @Override
        public void init() {
            throw new IllegalStateException("init failure");
        }

        @Override
        public void service(ServletRequest req, ServletResponse res) {}
    }

    static volatile boolean trapInitialized = false;

    // 초기화 실패 검증용 클래스
    public static class StaticInitTrap {
        static {
            trapInitialized = true;
        }
    }

    // SimpleServlet을 구현했지만 초기화에서 에러가 날 경우 예외가 번지지 않는 기능을 검증하는 클래스
    public static class BrokenStaticInitServlet implements SimpleServlet {
        static {
            if (true) {
                throw new IllegalStateException("static init failure");
            }
        }

        @Override
        public void service(ServletRequest req, ServletResponse res) {}
    }

    private static <T> void assertInstanceOf(Class<T> expected, Object actual) {
        assertTrue("Expected instance of " + expected.getName() + " but was " + actual.getClass().getName(),
                expected.isInstance(actual));
    }
}
