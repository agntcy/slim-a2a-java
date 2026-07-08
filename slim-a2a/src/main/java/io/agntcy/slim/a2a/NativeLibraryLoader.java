// Copyright AGNTCY Contributors (https://github.com/agntcy)
// SPDX-License-Identifier: Apache-2.0

package io.agntcy.slim.a2a;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * slim-bindings-java 1.4.x resolves its native library through the Java Foreign
 * Function &amp; Memory API instead of JNA, so it no longer self-extracts the
 * platform-specific binary bundled inside its jar. This extracts the matching
 * binary to a temp file and points
 * {@code uniffi.component.slim_bindings.libraryOverride} at it, restoring the
 * "just works" behavior the JNA-based 1.3.x loader used to provide.
 */
final class NativeLibraryLoader {

    private static final String OVERRIDE_PROPERTY = "uniffi.component.slim_bindings.libraryOverride";

    private NativeLibraryLoader() {}

    static synchronized void ensureExtracted() {
        if (System.getProperty(OVERRIDE_PROPERTY) != null) {
            return;
        }
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path cachedFile = tempDir.resolve("libslim_bindings_1_4_1" + suffix());
        if (Files.exists(cachedFile)) {
            try {
                if (Files.size(cachedFile) > 0) {
                    System.setProperty(OVERRIDE_PROPERTY, cachedFile.toAbsolutePath().toString());
                    return;
                }
            } catch (IOException ignored) {}
        }
        String resourcePath = platformDir() + "/" + libraryFileName();
        try (InputStream in = NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(
                        "slim-bindings-java native library not found on classpath: " + resourcePath);
            }
            Path tempFile = Files.createTempFile(tempDir, "slim_bindings_tmp", suffix());
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tempFile, cachedFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                cachedFile = tempFile;
            }
            System.setProperty(OVERRIDE_PROPERTY, cachedFile.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract slim-bindings-java native library", e);
        }
    }

    private static String platformDir() {
        return osFamily() + "-" + archName();
    }

    private static String libraryFileName() {
        return switch (osFamily()) {
            case "win32" -> "slim_bindings.dll";
            case "darwin" -> "libslim_bindings.dylib";
            default -> "libslim_bindings.so";
        };
    }

    private static String suffix() {
        return switch (osFamily()) {
            case "win32" -> ".dll";
            case "darwin" -> ".dylib";
            default -> ".so";
        };
    }

    private static String osFamily() {
        String name = System.getProperty("os.name", "").toLowerCase();
        if (name.contains("mac") || name.contains("darwin")) {
            return "darwin";
        }
        if (name.contains("win")) {
            return "win32";
        }
        return "linux";
    }

    private static String archName() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "aarch64";
        }
        return "x86-64";
    }
}
