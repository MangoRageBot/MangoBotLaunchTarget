package org.mangorage.mangobotlaunch.launch;

import org.mangorage.bootstrap.api.dependency.IDependency;
import org.mangorage.bootstrap.api.dependency.IDependencyLocator;
import org.mangorage.bootstrap.api.launch.ILaunchTarget;
import org.mangorage.bootstrap.api.launch.ILaunchTargetEntrypoint;
import org.mangorage.mangobotlaunch.util.Util;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

public final class MangoBotLaunchTarget implements ILaunchTarget {

    /**
     * Deletes the target directory if it exists, then copies all files from the list into it.
     *
     * @param files      List of files to copy
     * @param targetDir  Directory to copy files into
     * @throws IOException if anything goes wrong
     */
    public static void copyFilesToDirectory(List<Path> files, Path targetDir) throws IOException {
        // Delete directory if it exists
        if (Files.exists(targetDir)) {
            deleteDirectoryRecursively(targetDir);
        }

        // Recreate the empty directory
        Files.createDirectories(targetDir);

        // Copy each file
        for (Path file : files) {
            if (!Files.isRegularFile(file)) continue; // skip garbage
            Path dest = targetDir.resolve(file.getFileName());
            Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteDirectoryRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;

        // Walk the directory bottom-up and delete everything
        Files.walk(dir)
                .sorted(Comparator.reverseOrder()) // delete children first
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to delete " + path, e);
                    }
                });
    }

    @Override
    public String getId() {
        return "mangobot";
    }

    @Override
    public void launch(ModuleLayer bootstrapLayer, ModuleLayer parent, String[] args) throws Throwable {
        final var pluginsPath = Path.of("plugins");

        List<IDependencyLocator> dependencyLocators = ServiceLoader.load(IDependencyLocator.class, getClass().getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        final Map<String, List<IDependency>> dependencies = new HashMap<>();

        dependencyLocators.forEach(locator -> {
            if (locator.isValidLocatorFor(getId())) {
                final var foundDependencies = locator.locate();
                foundDependencies.forEach(dependency -> {
                    dependencies.computeIfAbsent(dependency.getName(), k -> new ArrayList<>()).add(dependency);
                });
            }
        });

        final Map<String, IDependency> finalDependencies = new HashMap<>();

        dependencies.forEach((id, results) -> {
            final IDependency bestResult = results.stream()
                    .min(Comparator.comparingInt(r -> r.getModuleNameOrigin().ordinal()))
                    .get();
            finalDependencies.put(bestResult.getName(), bestResult);
        });

        Set<String> moduleNames = new HashSet<>();
        moduleNames.addAll(Util.getModuleNames(pluginsPath));
        moduleNames.addAll(finalDependencies.keySet());

        System.out.println("----------------------------------------------");
        System.out.println("Module Info");
        System.out.println("----------------------------------------------");
        moduleNames.forEach(name -> {
            System.out.println("Module Name -> " + name);
        });
        System.out.println("----------------------------------------------");
        System.out.println("Module -> Jar Info");
        System.out.println("----------------------------------------------");
        finalDependencies.forEach((module, result) -> {
            System.out.println("Module -> " + module + " Jar -> " + result.resolveJar() + " Name -> " + result.getName() + " Origin -> " + result.getModuleNameOrigin());
        });
        System.out.println("----------------------------------------------");

        final var moduleCfg = Configuration.resolve(
                ModuleFinder.of(
                        finalDependencies.values()
                                .stream()
                                .map(IDependency::resolveJar)
                                .toArray(Path[]::new)
                ),
                List.of(
                        parent.configuration()
                ),
                ModuleFinder.of(
                        pluginsPath
                ),
                moduleNames
        );

        final var moduleCL = new MangoLoaderImpl(moduleCfg.modules(), Thread.currentThread().getContextClassLoader());

        final var moduleLayerController = ModuleLayer.defineModules(moduleCfg, List.of(parent), s -> moduleCL);
        final var moduleLayer = moduleLayerController.layer();

        Thread.currentThread().setContextClassLoader(moduleCL);

        moduleCL.load(moduleLayer, moduleLayerController);

        ServiceLoader.load(ILaunchTargetEntrypoint.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(entrypoint -> entrypoint.getLaunchTargetId().equals("mangobot"))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Unable to find entrypoint for mangobot launch target"))
                .init(args);
    }
}
