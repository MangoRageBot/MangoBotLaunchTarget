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
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MangoLoaderImpl extends SecureClassLoader implements IMangoLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private ClassTransformers transformers = new ClassTransformers(this);
    private final Map<String, LoadedModule> moduleMap = new ConcurrentHashMap<>();
    private final Map<String, LoadedModule> localPackageToModule = new ConcurrentHashMap<>();

    public MangoLoaderImpl(Set<ResolvedModule> modules, ClassLoader parent) {
        super(parent);

        modules.forEach(module -> {
            try {
                final var loadedModule = new LoadedModule(module.reference());

                moduleMap.put(
                        module.name(),
                        loadedModule
                );

                module.reference().descriptor().packages().forEach(pkg -> {
                    localPackageToModule.put(pkg, loadedModule);
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    void load(final ModuleLayer moduleLayer, final ModuleLayer.Controller controller) {
        loadModuleConfiguration(moduleLayer, controller);
        loadTransformers();
    }

    void loadModuleConfiguration(final ModuleLayer moduleLayer, final ModuleLayer.Controller controller) {
        final var moduleLayerImpl = new ModuleLayerImpl(moduleLayer, controller);
        ServiceLoader.load(IModuleConfigurator.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .forEach(configurator -> {
                    configurator.configureModuleLayer(moduleLayerImpl);

                    moduleMap.forEach((id, module) -> {
                        final var children = configurator.getChildren(id);

                        children
                                .stream()
                                .filter(moduleMap::containsKey)
                                .map(moduleMap::get)
                                .forEach(module::addChild);
                    });
                });
    }

    void loadTransformers() {
        ServiceLoader.load(IClassTransformer.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .forEach(transformer -> {
                    transformers.add(transformer);
                });
    }

    @Override
    public boolean hasClass(final String name) {
        final String canonicalName = name.replace('/', '.');
        return this.findLoadedClass(canonicalName) != null;
    }

    /**
     * Loads the class with the specified binary name.
     */
    @Override
    protected Class<?> loadClass(String cn, boolean resolve) throws ClassNotFoundException
    {

        synchronized (getClassLoadingLock(cn)) {
            // check if already loaded
            Class<?> c = findLoadedClass(cn);

            if (c == null) {

                LoadedModule loadedModule = findLoadedModule(cn);

                if (loadedModule != null) {

                    // class is in module defined to this class loader
                    c = defineClass(cn, loadedModule);

                } else {
                    return getParent().loadClass(cn);
                }
            }

            if (c == null)
                throw new ClassNotFoundException(cn);

            if (resolve)
                resolveClass(c);

            return c;
        }
    }

    @Override
    public URL findResource(String name) {
        String pn = toPackageName(name);
        LoadedModule module = localPackageToModule.get(pn);

        if (module != null) {
            try {
                URL url = findResource(module.name(), name);
                if (url != null
                        && (name.endsWith(".class")
                        || url.toString().endsWith("/")
                        || isOpen(module.getModuleReference(), pn))) {
                    return url;
                }
            } catch (IOException ioe) {
                // ignore
            }

        } else {
            for (LoadedModule mref : moduleMap.values()) {
                try {
                    URL url = findResource(mref.name(), name);
                    if (url != null) return url;
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }

        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        return Collections.enumeration(findResourcesAsList(name));
    }

    /**
     * Finds the resources with the given name in this class loader.
     */
    private List<URL> findResourcesAsList(String name) throws IOException {
        String pn = toPackageName(name);
        LoadedModule module = localPackageToModule.get(pn);
        if (module != null) {
            URL url = findResource(module.name(), name);
            if (url != null
                    && (name.endsWith(".class")
                    || url.toString().endsWith("/")
                    || isOpen(module.getModuleReference(), pn))) {
                return List.of(url);
            } else {
                return Collections.emptyList();
            }
        } else {
            List<URL> urls = new ArrayList<>();
            for (LoadedModule mref : moduleMap.values()) {
                URL url = findResource(mref.name(), name);
                if (url != null) {
                    urls.add(url);
                }
            }
            return urls;
        }
    }


    @Override
    protected URL findResource(String moduleName, String name) throws IOException {
        final var loadedModule = moduleMap.get(moduleName);

        if (loadedModule != null) {
            final var uri = loadedModule.find(name);
            if (uri.isPresent())
                return uri.get().toURL();
        }

        return null;
    }

    @Override
    protected Class<?> findClass(String cn) throws ClassNotFoundException {
        Class<?> c = null;
        LoadedModule loadedModule = findLoadedModule(cn);
        if (loadedModule != null)
            c = defineClass(cn, loadedModule);
        if (c == null)
            throw new ClassNotFoundException(cn);
        return c;
    }

    @Override
    protected Class<?> findClass(String moduleName, String name) {
        Class<?> c = null;
        LoadedModule loadedModule = findLoadedModule(name);
        if (loadedModule != null && loadedModule.getModuleReference().descriptor().name().equals(moduleName)) {
            c = defineClass(name, loadedModule);
        } else if (loadedModule != null) {
            throw new IllegalArgumentException("Expected Class '%s' in module '%s', instead was in '%s'".formatted(name, moduleName, loadedModule.getModuleReference().descriptor().name()));
        }
        return c;
    }

    @Override
    public byte[] getClassBytes(String cn) {
        LoadedModule loadedModule = findLoadedModule(cn);
        if (loadedModule != null) {
            ModuleReader reader = loadedModule.getModuleReader();
            String rn = cn.replace('.', '/').concat(".class");
            ByteBuffer bb;
            try {
                bb = reader.read(rn).orElse(null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (bb == null) {
                // class not found
                return null;
            }
            return bb.array();
        }

        String path = cn.replace('.', '/') + ".class";
        try (InputStream is = getParent().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Could not find class resource: " + path);
            }
            return is.readAllBytes(); // Java 9+
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Defines the given binary class name to the VM, loading the class
     * bytes from the given module.
     *
     * @return the resulting Class or {@code null} if an I/O error occurs
     */
    private Class<?> defineClass(String cn, LoadedModule loadedModule) {
        ModuleReader reader = loadedModule.getModuleReader();

        try {
            // read class file
            String rn = cn.replace('.', '/').concat(".class");
            ByteBuffer bb = reader.read(rn).orElse(null);
            if (bb == null) {
                // class not found
                return null;
            }

            if (transformers.containsClass(cn))
                return transformers.getClazz(cn);

            byte[] classbytes = bb.array();

            byte[] classBytesModified = transformers.transform(cn, classbytes);

            if (classBytesModified != null) {
                Class<?> clz = defineClass(cn, classBytesModified, 0, classBytesModified.length, loadedModule.getCodeSource());
                transformers.add(cn, clz);
                return clz;
            } else {
                try {
                    return defineClass(cn, bb, loadedModule.getCodeSource());
                } finally {
                    reader.release(bb);
                }
            }
        } catch (IOException ioe) {
            // TBD on how I/O errors should be propagated
            return null;
        }
    }

    /**
     * Find the candidate module for the given class name.
     * Returns {@code null} if none of the modules defined to this
     * class loader contain the API package for the class.
     */
    private LoadedModule findLoadedModule(String cn) {
        String pn = packageName(cn);
        return pn.isEmpty() ? null : localPackageToModule.get(pn);
    }

    /**
     * Returns the package name for the given class name
     */
    private String packageName(String cn) {
        int pos = cn.lastIndexOf('.');
        return (pos < 0) ? "" : cn.substring(0, pos);
    }

    /**
     * Derive a <em>package name</em> for a resource. The package name
     * returned by this method may not be a legal package name. This method
     * returns null if the resource name ends with a "/" (a directory)
     * or the resource name does not contain a "/".
     */
    private static String toPackageName(String name) {
        int index = name.lastIndexOf('/');
        if (index == -1 || index == name.length()-1) {
            return "";
        } else {
            return name.substring(0, index).replace('/', '.');
        }
    }

    /**
     * Returns true if the given module opens the given package
     * unconditionally.
     *
     * @implNote This method currently iterates over each of the open
     * packages. This will be replaced once the ModuleDescriptor.Opens
     * API is updated.
     */
    private boolean isOpen(ModuleReference mref, String pn) {
        ModuleDescriptor descriptor = mref.descriptor();
        if (descriptor.isOpen() || descriptor.isAutomatic())
            return true;
        for (ModuleDescriptor.Opens opens : descriptor.opens()) {
            String source = opens.source();
            if (!opens.isQualified() && source.equals(pn)) {
                return true;
            }
        }
        return false;
    }
}
