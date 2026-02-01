package org.mangorage.mangobotlaunch.util.handler;

import org.mangorage.bootstrap.api.dependency.IDependency;
import org.mangorage.bootstrap.api.util.GsonUtil;
import org.mangorage.mangobotlaunch.util.Dependencies;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class DependencyHandler {

    public static List<IDependency> scanPackages(Path packagesPath, Path librariesPath) throws IOException {

        final List<IDependency> results = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(packagesPath)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    final Dependencies dependenciesList = GsonUtil.get(
                            Dependencies.class,
                            readFileFromJar(entry, "installer-data/dependencies.json")
                    );

                    dependenciesList.dependencies().forEach(dependency -> {
                        final var result = JarHandler.resolveModuleName(
                                librariesPath.resolve(dependency.output())
                        );
                        results.add(result);
                    });
                }
            }
        }

        return results;
    }


    public static String readFileFromJar(Path jarPath, String entryPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(entryPath);

            if (entry == null) {
                throw new FileNotFoundException("Entry not found in jar: " + entryPath);
            }

            try (InputStream in = jar.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
