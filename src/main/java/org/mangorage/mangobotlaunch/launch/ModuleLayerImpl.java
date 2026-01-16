package org.mangorage.mangobotlaunch.launch;

import org.mangorage.bootstrap.api.module.IModuleLayer;

public final class ModuleLayerImpl implements IModuleLayer {
    private final ModuleLayer layer;
    private final ModuleLayer.Controller controller;

    ModuleLayerImpl(ModuleLayer layer, ModuleLayer.Controller controller) {
        this.layer = layer;
        this.controller = controller;
    }

    @Override
    public void addOpens(String sourceModule, String pkg, String targetModule) {
        final var source = layer.findModule(sourceModule);
        final var target = layer.findModule(targetModule);
        if (source.isEmpty() || target.isEmpty()) return;
        controller.addOpens(source.get(), pkg, target.get());
    }

    @Override
    public void addExports(String sourceModule, String pkg, String targetModule) {
        final var source = layer.findModule(sourceModule);
        final var target = layer.findModule(targetModule);
        if (source.isEmpty() || target.isEmpty()) return;
        controller.addExports(source.get(), pkg, target.get());
    }

    @Override
    public void addReads(String sourceModule, String targetModule) {
        final var source = layer.findModule(sourceModule);
        final var target = layer.findModule(targetModule);
        if (source.isEmpty() || target.isEmpty()) return;
        controller.addReads(source.get(), target.get());
    }
}
