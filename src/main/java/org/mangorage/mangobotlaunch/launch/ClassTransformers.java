package org.mangorage.mangobotlaunch.launch;

import org.mangorage.bootstrap.api.transformer.IClassTransformer;
import org.mangorage.bootstrap.api.transformer.TransformResult;
import org.mangorage.bootstrap.api.transformer.TransformerFlag;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class ClassTransformers {
    private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();
    private final List<IClassTransformer> transformers = new CopyOnWriteArrayList<>(); // Transformer's
    private final ClassLoader loader;

    ClassTransformers(ClassLoader loader) {
        this.loader = loader;
    }

    void add(String name, Class<?> clz) {
        classes.put(name, clz);
    }

    void add(IClassTransformer transformer) {
        transformers.add(transformer);
    }

    boolean isEmpty() {
        return transformers.isEmpty();
    }

    byte[] transform(String name, byte[] classData) {;

        AtomicReference<TransformResult> result = new AtomicReference<>(TransformerFlag.NO_REWRITE.of(classData));
        AtomicReference<IClassTransformer> _transformer = new AtomicReference<>();

        for (IClassTransformer transformer : transformers) {
            result.set(transformer.transform(name, classData));
            if (result.get().flag() != TransformerFlag.NO_REWRITE) {
                _transformer.set(transformer);
                break;
            }
        }

        if (result.get().flag() != TransformerFlag.NO_REWRITE && _transformer.get() != null) {
            System.out.println("%s Transformed %s".formatted(_transformer.get().getName(), name));
            return result.get().classData();
        }

        return null;
    }

    boolean containsClass(String name) {
        return classes.containsKey(name);
    }

    Class<?> getClazz(String string) {
        return classes.get(string);
    }
}