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
 * slim-bindings-java resolves its native library through the Java Foreign
 * Function &amp; Memory API instead of JNA, so it does not self-extract the
 * platform-specific binary bundled inside its jar. This extracts the matching
 * binary to a temp file and points the UniFFI library-override properties at
 * it, restoring the "just works" behavior the JNA-based 1.3.x loader used to
 * provide.
 *
 * <p>slim-bindings-java 2.0 exposes two UniFFI namespaces — {@code slim_bindings}
 * for the transport and {@code slim_rpc} for SlimRPC — that are compiled into the
 * same shared object, so both overrides are pointed at the same extracted file.
 */
final class NativeLibraryLoader {

    private static final String[] OVERRIDE_PROPERTIES = {
            "uniffi.component.slim_bindings.libraryOverride",
            "uniffi.component.slim_rpc.libraryOverride",
    };

    private NativeLibraryLoader() {}

    static synchronized void ensureExtracted() {
        String existing = null;
        for (String property : OVERRIDE_PROPERTIES) {
            String value = System.getProperty(property);
            if (value != null) {
                existing = value;
            }
        }
        if (existing == null) {
            existing = extract();
        }
        for (String property : OVERRIDE_PROPERTIES) {
            if (System.getProperty(property) == null) {
                System.setProperty(property, existing);
            }
        }
    }

    private static String extract() {
        String resourcePath = platformDir() + "/" + libraryFileName();
        try (InputStream in = NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(
                        "slim-bindings-java native library not found on classpath: " + resourcePath);
            }
            Path tempFile = Files.createTempFile("slim_bindings-", suffix());
            tempFile.toFile().deleteOnExit();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return tempFile.toAbsolutePath().toString();
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
