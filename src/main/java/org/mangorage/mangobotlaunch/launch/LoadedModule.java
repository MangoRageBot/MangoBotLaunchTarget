package org.mangorage.mangobotlaunch.launch;

import java.io.IOException;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LoadedModule {
    private final List<LoadedModule> children = new ArrayList<>();

    private final ModuleReference moduleReference;

    LoadedModule(ModuleReference moduleReference) {
        this.moduleReference = moduleReference;
    }

    ModuleReference getModuleReference() {
        return moduleReference;
    }

    ModuleReader getModuleReader() {
        try {
            return moduleReference.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void addChild(LoadedModule module) {
        this.children.add(module);
    }

    String name() {
        return getModuleReference().descriptor().name();
    }

    public Optional<URI> find(String name) throws IOException {
        final var optional = getModuleReader().find(name);
        if (optional.isPresent()) return optional;

        for (LoadedModule child : children) {
            final var optionalChild = child.getModuleReader().find(name);
            if (optionalChild.isPresent())
                return optionalChild;
        }

        return Optional.empty();
    }
}
