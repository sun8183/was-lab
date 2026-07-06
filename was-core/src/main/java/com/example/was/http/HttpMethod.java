package com.example.was.http;

public enum HttpMethod {
    GET;

    public static boolean isSupported(String method) {
        for (HttpMethod m : values()) {
            if (m.name().equalsIgnoreCase(method)) return true;
        }
        return false;
    }
}
