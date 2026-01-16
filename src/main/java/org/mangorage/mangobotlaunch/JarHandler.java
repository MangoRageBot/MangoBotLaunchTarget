package org.mangorage.mangobotlaunch;

import org.mangorage.bootstrap.api.dependency.ModuleNameOrigin;
import org.mangorage.mangobotlaunch.util.Result;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class JarHandler {

    public static Result resolveModuleName(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {

            String moduleName = null;

            try {
                moduleName = ModuleFinder.of(jarPath)
                        .findAll()
                        .iterator()
                        .next()
                        .descriptor()
                        .name();
            } catch (Exception ignore) {}


            // 1. Proper JPMS module
            if (jarFile.getEntry("module-info.class") != null) {
                return new Result(
                        ModuleFinder.of(jarPath)
                                .findAll()
                                .iterator()
                                .next()
                                .descriptor()
                                .name(),
                        ModuleNameOrigin.MODULE_INFO,
                        jarPath
                );
            } else if (jarFile.isMultiRelease() && moduleName != null) {
                return new Result(moduleName, ModuleNameOrigin.MULTI_RELEASE, jarPath);
            }

            // 2. Check MANIFEST.MF for Automatic-Module-Name
            Manifest manifest = jarFile.getManifest();

            if (manifest != null) {
                String autoName = manifest.getMainAttributes()
                        .getValue("Automatic-Module-Name");

                if (autoName != null && !autoName.isBlank()) {
                    return new Result(
                            autoName,
                            ModuleNameOrigin.MANIFEST,
                            jarPath
                    );
                }

                try {
                    final var found = ModuleFinder.of(jarPath).findAll();
                    if (found != null) {
                        final var foundModule = found.stream().findAny();
                        if (foundModule.isPresent())
                            System.out.println(foundModule.get());
                        return new Result(
                                foundModule.get().descriptor().name(),
                                ModuleNameOrigin.MODULE_FINDER,
                                jarPath
                        );
                    }
                } catch (Exception ignore) {
                    ignore.printStackTrace();
                }


                String symbolicName = manifest.getMainAttributes()
                        .getValue("Bundle-SymbolicName");

                if (symbolicName != null) {
                    return new Result(
                            symbolicName,
                            ModuleNameOrigin.MANIFEST_BUNDLE_SYMBOLIC_NAME,
                            jarPath
                    );
                }

            }

            // 3. Fallback: filename heuristic (aka desperation mode)
            String filename = jarPath.getFileName().toString();

            String cleanedName = filename
                    .replaceAll("-[\\d\\.]+.*\\.jar$", "") // Remove version and extension
                    .replaceAll("\\.jar$", "")             // Remove extension if no version
                    .replace('-', '.');                    // Convert hyphens to dots

            return new Result(
                    cleanedName,
                    ModuleNameOrigin.GUESSED,
                    jarPath
            );

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JAR: " + jarPath, e);
        }
    }

}
