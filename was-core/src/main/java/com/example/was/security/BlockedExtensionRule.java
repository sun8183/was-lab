package com.example.was.security;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

public class BlockedExtensionRule implements ForbiddenRule {

    private final Set<String> blockedExtensions;

    public BlockedExtensionRule(Set<String> blockedExtensions) {
        this.blockedExtensions = blockedExtensions.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean matches(Path httpRoot, Path resolvedPath) {
        String fileName = resolvedPath.getFileName().toString().toLowerCase();
        return blockedExtensions.stream().anyMatch(fileName::endsWith);
    }
}
