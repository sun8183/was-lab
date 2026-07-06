package com.example.was.servlet;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DirectClassServletMapper implements ServletMapper {

    private final Map<String, SimpleServlet> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<SimpleServlet> resolve(String requestPath) {
        String className = toClassName(requestPath);
        if (className.isEmpty()) {
            return Optional.empty();
        }
        try {
            // createServlet() 이 null 을 반환하면 ConcurrentHashMap 은 해당 키를 저장하지 않는다.
            // SimpleServlet 이 아닌 클래스는 매 요청마다 재검사되지만, 정상 경로에서는 발생하지 않는다.
            SimpleServlet servlet = cache.computeIfAbsent(className, this::createServlet);
            return Optional.ofNullable(servlet);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public void destroyAll() {
        cache.values().forEach(servlet -> {
            try {
                servlet.destroy();
            } catch (Exception ignored) {}
        });
    }

    @SuppressWarnings("unchecked")
    private SimpleServlet createServlet(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!SimpleServlet.class.isAssignableFrom(clazz)) {
                return null;
            }
            SimpleServlet servlet = ((Class<? extends SimpleServlet>) clazz)
                    .getDeclaredConstructor().newInstance();
            servlet.init();
            return servlet;
        } catch (ReflectiveOperationException | NoClassDefFoundError e) {
            // NoClassDefFoundError 는 Error 상속이라 별도로 잡아야 위로 전파되지 않는다.
            System.err.println("Cannot create servlet for class " + e);
            throw new RuntimeException(e);
        }
    }

    private static String toClassName(String requestPath) {
        int questionMark = requestPath.indexOf('?');
        String pathOnly = questionMark < 0 ? requestPath : requestPath.substring(0, questionMark);
        return pathOnly.startsWith("/") ? pathOnly.substring(1) : pathOnly;
    }
}
