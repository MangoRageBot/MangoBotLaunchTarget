package org.mangorage.mangobotlaunch.launch;

import java.io.IOException;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class LoadedModule implements ModuleReader {
    private final List<LoadedModule> children = new ArrayList<>();

    private final ModuleReference moduleReference;
    private final ModuleReader moduleReader;
    private final CodeSource codeSource;

    LoadedModule(ModuleReference moduleReference) throws IOException {
        this.moduleReference = moduleReference;
        this.moduleReader = moduleReference.open();
        this.codeSource = new CodeSource(moduleReference.location().get().toURL(), (CodeSigner[]) null);
    }

    ModuleReference getModuleReference() {
        return moduleReference;
    }

    ModuleReader getModuleReader() {
        return moduleReader;
    }

    CodeSource getCodeSource() {
        return codeSource;
    }

    void addChild(LoadedModule module) {
        this.children.add(module);
    }

    String name() {
        return getModuleReference().descriptor().name();
    }

    @Override
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

    @Override
    public Stream<String> list() throws IOException {
        return Stream.empty();
    }

    @Override
    public void close() throws IOException {

    }
}
