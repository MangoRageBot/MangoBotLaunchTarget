package org.mangorage.mangobotlaunch.util;

import java.io.File;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class Util {

    public static Set<String> getModuleNames(Path folder) {
        final Set<String> moduleNames = new HashSet<>();

        if (folder == null || !folder.toFile().isDirectory()) {
            throw new IllegalArgumentException("That's not a valid folder, genius: " + folder);
        }

        final var jarFiles = folder.toFile().listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null) return moduleNames;

        for (final var jarFile : jarFiles) {
            final var name = getModuleName(jarFile);
            if (name != null) moduleNames.add(name);
        }

        return moduleNames;
    }

    public static String getModuleName(File jarFile) {
        if (jarFile == null || !jarFile.isFile() || !jarFile.getName().endsWith(".jar")) {
            throw new IllegalArgumentException("Not a valid jar file, genius: " + jarFile);
        }

        try {
            ModuleFinder finder = ModuleFinder.of(jarFile.toPath());
            Set<ModuleReference> modules = finder.findAll();

            for (ModuleReference moduleRef : modules) {
                var descriptor = moduleRef.descriptor();
                return descriptor.name(); // Return the first (and only) module name
            }
        } catch (Exception e) {
            System.err.println("Couldn't process " + jarFile.getName() + ": " + e.getMessage());
        }

        return null; // Jar was either not modular or you're just unlucky
    }

    public static void callMain(String className, String[] args, Module module) {
        try {
            Class<?> clazz = Class.forName(className, false, module.getClassLoader());
            Method mainMethod = clazz.getMethod("main", String[].class);

            // Make sure it's static and public
            if (!java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers())) {
                throw new IllegalStateException("Main method is not static, are you high?");
            }

            // Invoke the main method with a godawful cast
            mainMethod.invoke(null, (Object) args);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("Couldn't reflectively call main because something exploded.", e);
        }
    }
}
