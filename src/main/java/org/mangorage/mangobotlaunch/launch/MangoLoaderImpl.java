package org.mangorage.mangobotlaunch.launch;

import org.mangorage.bootstrap.api.loader.IMangoLoader;
import org.mangorage.bootstrap.api.module.IModuleConfigurator;
import org.mangorage.bootstrap.api.transformer.IClassTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MangoLoaderImpl extends ClassLoader implements IMangoLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final ClassTransformers transformers = new ClassTransformers();
    private final Map<String, LoadedModule> moduleMap = new ConcurrentHashMap<>();
    private final Map<String, LoadedModule> localPackageToModule = new ConcurrentHashMap<>();

    public MangoLoaderImpl(Set<ResolvedModule> modules, ClassLoader parent) {
        super(parent);
        initializeModules(modules);
    }

    private void initializeModules(Set<ResolvedModule> modules) {
        modules.forEach(module -> {
            var loadedModule = new LoadedModule(module.reference());
            moduleMap.put(module.name(), loadedModule);
            module.reference().descriptor().packages().forEach(pkg -> localPackageToModule.put(pkg, loadedModule));
        });
    }

    void load(final ModuleLayer moduleLayer, final ModuleLayer.Controller controller) {
        var moduleLayerImpl = new ModuleLayerImpl(moduleLayer, controller);
        ServiceLoader.load(IModuleConfigurator.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .forEach(configurator -> {
                    configurator.configureModuleLayer(moduleLayerImpl);
                    moduleMap.forEach((id, module) ->
                            configurator.getChildren(id).stream()
                                    .filter(moduleMap::containsKey)
                                    .map(moduleMap::get)
                                    .forEach(module::addChild)
                    );
                });

        ServiceLoader.load(IClassTransformer.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .forEach(transformers::add);
    }

    @Override
    public boolean hasClass(final String name) {
        return findLoadedClass(name.replace('/', '.')) != null;
    }

    @Override
    protected Class<?> loadClass(String cn, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(cn)) {
            Class<?> c = findLoadedClass(cn);
            if (c == null) {
                LoadedModule loadedModule = findLoadedModule(cn);
                if (loadedModule != null) {
                    c = defineClass(cn, loadedModule);
                } else {
                    return getParent().loadClass(cn);
                }
            }
            if (resolve) resolveClass(c);
            return c;
        }
    }

    @Override
    public URL findResource(String name) {
        String pn = toPackageName(name);
        LoadedModule module = localPackageToModule.get(pn);
        if (module != null) {
            return tryFindResource(module, name);
        }
        return moduleMap.values().stream()
                .map(moduleRef -> tryFindResource(moduleRef, name))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        return Collections.enumeration(findResourcesAsList(name));
    }

    private List<URL> findResourcesAsList(String name){
        String pn = toPackageName(name);
        LoadedModule module = localPackageToModule.get(pn);
        if (module != null) {
            URL url = tryFindResource(module, name);
            return url == null ? Collections.emptyList() : List.of(url);
        }
        List<URL> urls = new ArrayList<>();
        for (LoadedModule mref : moduleMap.values()) {
            URL url = tryFindResource(mref, name);
            if (url != null) urls.add(url);
        }
        return urls;
    }

    private URL tryFindResource(LoadedModule module, String name) {
        try {
            URL url = findResource(module.name(), name);
            return (url != null && (name.endsWith(".class") || url.toString().endsWith("/") || isOpen(module.getModuleReference(), toPackageName(name)))) ? url : null;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected URL findResource(String moduleName, String name) throws IOException {
        LoadedModule loadedModule = moduleMap.get(moduleName);
        if (loadedModule != null) {
            var uri = loadedModule.find(name);
            if (uri.isPresent()) return uri.get().toURL();
        }
        return null;
    }

    @Override
    protected Class<?> findClass(String cn) throws ClassNotFoundException {
        LoadedModule loadedModule = findLoadedModule(cn);
        if (loadedModule != null) return defineClass(cn, loadedModule);
        throw new ClassNotFoundException(cn);
    }

    @Override
    protected Class<?> findClass(String moduleName, String name) {
        LoadedModule loadedModule = findLoadedModule(name);
        if (loadedModule != null && loadedModule.getModuleReference().descriptor().name().equals(moduleName)) {
            return defineClass(name, loadedModule);
        } else if (loadedModule != null) {
            throw new IllegalArgumentException(String.format("Expected Class '%s' in module '%s', but found in '%s'", name, moduleName, loadedModule.getModuleReference().descriptor().name()));
        }
        return null;
    }

    @Override
    public byte[] getClassBytes(String cn) {
        LoadedModule loadedModule = findLoadedModule(cn);
        return loadedModule != null ? getClassBytesFromModule(loadedModule, cn) : getClassBytesFromParent(cn);
    }

    private byte[] getClassBytesFromModule(LoadedModule loadedModule, String cn) {
        try {
            ModuleReader reader = loadedModule.getModuleReader();
            String rn = cn.replace('.', '/') + ".class";
            ByteBuffer bb = reader.read(rn).orElse(null);
            return bb == null ? null : bb.array();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] getClassBytesFromParent(String cn) {
        String path = cn.replace('.', '/') + ".class";
        try (InputStream is = getParent().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Could not find class resource: " + path);
            return is.readAllBytes(); // Java 9+
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Class<?> defineClass(String cn, LoadedModule loadedModule) {
        try {
            ModuleReader reader = loadedModule.getModuleReader();
            String rn = cn.replace('.', '/').concat(".class");
            ByteBuffer bb = reader.read(rn).orElse(null);
            if (bb == null) return null;

            byte[] classBytes = bb.array();
            byte[] modifiedClassBytes = transformers.transform(cn, classBytes);

            if (modifiedClassBytes != null) {
                Class<?> clz = defineClass(cn, modifiedClassBytes, 0, modifiedClassBytes.length);
                transformers.add(cn, clz);
                return clz;
            }
            return defineClass(cn, classBytes, 0, classBytes.length);
        } catch (IOException ioe) {
            return null;
        }
    }

    private LoadedModule findLoadedModule(String cn) {
        String pn = packageName(cn);
        return pn.isEmpty() ? null : localPackageToModule.get(pn);
    }

    private String packageName(String cn) {
        int pos = cn.lastIndexOf('.');
        return (pos < 0) ? "" : cn.substring(0, pos);
    }

    private static String toPackageName(String name) {
        int index = name.lastIndexOf('/');
        return (index == -1 || index == name.length() - 1) ? "" : name.substring(0, index).replace('/', '.');
    }

    private boolean isOpen(ModuleReference mref, String pn) {
        if (pn.isEmpty()) return true; // Open if its well, root...

        ModuleDescriptor descriptor = mref.descriptor();
        if (descriptor.isOpen() || descriptor.isAutomatic()) return true;
        return descriptor.opens().stream().anyMatch(opens -> !opens.isQualified() && opens.source().equals(pn));
    }
}
