package com.example.was.security;

import java.nio.file.Path;

public class PathTraversalRule implements ForbiddenRule {

    @Override
    public boolean matches(Path httpRoot, Path resolvedPath) {
        return !resolvedPath.startsWith(httpRoot);
    }
}
