package com.example.was.security;

import java.nio.file.Path;

public interface ForbiddenRule {

    boolean matches(Path httpRoot, Path resolvedPath);
}
