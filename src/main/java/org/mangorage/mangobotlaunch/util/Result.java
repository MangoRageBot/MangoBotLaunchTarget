package org.mangorage.mangobotlaunch.util;

import org.mangorage.bootstrap.api.dependency.IDependency;
import org.mangorage.bootstrap.api.dependency.ModuleNameOrigin;

import java.nio.file.Path;

public record Result(String name, ModuleNameOrigin origin, Path jar) implements IDependency {
    @Override
    public String getName() {
        return name;
    }

    @Override
    public ModuleNameOrigin getModuleNameOrigin() {
        return origin;
    }

    @Override
    public Path resolveJar() {
        return jar;
    }
}
