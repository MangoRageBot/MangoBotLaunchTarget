package org.mangorage.mangobotlaunch.launch;

import org.mangorage.bootstrap.api.dependency.IDependency;
import org.mangorage.bootstrap.api.dependency.IDependencyLocator;
import org.mangorage.mangobotlaunch.util.handler.DependencyHandler;

import java.nio.file.Path;
import java.util.List;

public class MangoBotDependencyLocator implements IDependencyLocator {
    @Override
    public boolean isValidLocatorFor(String launchTarget) {
        return launchTarget.equals("mangobot");
    }

    @Override
    public List<IDependency> locate() {
        final var librariesPath = Path.of("libraries");
        final var pluginsPath = Path.of("plugins");

        try {
            return DependencyHandler.scanPackages(pluginsPath.toAbsolutePath(), librariesPath.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("Failed to locate dependencies in " + getClass() + " cause -> " + e);
            return List.of();
        }
    }


}
