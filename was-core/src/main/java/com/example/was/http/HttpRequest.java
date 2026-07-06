package com.example.was.http;

import java.util.Map;

public record HttpRequest(String method, String path, String version, Map<String, String> headers) {
}
