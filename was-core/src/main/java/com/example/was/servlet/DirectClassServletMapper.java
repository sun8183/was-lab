package com.example.was.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DirectClassServletMapper implements ServletMapper {

    private static final Logger log = LoggerFactory.getLogger(DirectClassServletMapper.class);
    private static final int MAX_NON_SERVLET_CACHE_SIZE = 1000;

    private final Map<String, SimpleServlet> cache = new ConcurrentHashMap<>();

    // 서블릿이 아닌 경로(정적 파일 요청 등)를 기억해, 매 요청마다 클래스패스 탐색 + ClassNotFoundException 생성을 반복하지 않는다.
    // 임의 경로 요청으로 무한히 커지지 않도록 크기를 제한하고, 넘치면 가장 오래된 항목부터 버린다. 
    // removeEldestEntry 는 조건이 TRUE 인 경우 내부적으로 가장 오래된 항목을 지운다.
    // 기본은 항상 FALSE를 반환해서 오버라이드로 재정의 (Map에서 지원하는 함수)
    private final Set<String> nonServlets = Collections.newSetFromMap(Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_NON_SERVLET_CACHE_SIZE;
                }
            }));

    @Override
    public Optional<SimpleServlet> resolve(String requestPath) {
        String className = toClassName(requestPath);
        if (className.isEmpty() || nonServlets.contains(className)) {
            return Optional.empty();
        }
        // createServlet() 이 null 을 반환하면 ConcurrentHashMap 은 해당 키를 저장하지 않으므로 nonServlets 에 따로 기록한다.
        // 생성 실패(ServletInitException)는 캐시하지 않고 호출자에게 그대로 전파해 500 으로 응답하게 한다.
        SimpleServlet servlet = cache.computeIfAbsent(className, this::createServlet);
        if (servlet == null) {
            nonServlets.add(className);
        }
        return Optional.ofNullable(servlet);
    }

    @Override
    public void destroyAll() {
        cache.values().forEach(servlet -> {
            try {
                servlet.destroy();
            } catch (Exception ignored) {}
        });
    }

    /**
     * 서블릿이 아니면(클래스 없음, SimpleServlet 미구현) null 을 반환하고,
     * 서블릿이지만 생성/초기화에 실패하면 ServletInitException 을 던진다.
     */
    private SimpleServlet createServlet(String className) {
        Class<?> clazz;
        try {
            // initialize=false: 로딩만 하고 static 초기화는 하지 않는다.
            // URL 로 지정된 임의 클래스의 static 코드가 서블릿 판정 전에 실행되지 않도록 하기 위함.
            clazz = Class.forName(className, false, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        } catch (LinkageError e) {
            log.error("Cannot load servlet class {}", className, e);
            throw new ServletInitException(className, e);
        }

        if (!SimpleServlet.class.isAssignableFrom(clazz)) {
            return null;
        }

        try {
            // 서블릿으로 확인된 클래스만 여기서 처음 초기화된다.
            SimpleServlet servlet = clazz.asSubclass(SimpleServlet.class).getDeclaredConstructor().newInstance();
            servlet.init();
            return servlet;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            // ExceptionInInitializerError, NoClassDefFoundError 모두 LinkageError(Error 계열) 하위라 별도로 잡아야 위로 전파되지 않는다.
            // RuntimeException 은 init() 에서 던진 예외다.
            log.error("Cannot create servlet for class {}", className, e);
            throw new ServletInitException(className, e);
        }
    }

    private static String toClassName(String requestPath) {
        int questionMark = requestPath.indexOf('?');
        String pathOnly = questionMark < 0 ? requestPath : requestPath.substring(0, questionMark);
        return pathOnly.startsWith("/") ? pathOnly.substring(1) : pathOnly;
    }
}