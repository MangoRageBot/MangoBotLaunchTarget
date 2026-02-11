package org.mangorage.mangobotlaunch.launch;

import org.mangorage.bootstrap.api.transformer.IClassTransformer;
import org.mangorage.bootstrap.api.transformer.IClassTransformerHistory;
import org.mangorage.bootstrap.api.transformer.ITransformerResultHistory;
import org.mangorage.bootstrap.api.transformer.TransformResult;
import org.mangorage.bootstrap.api.transformer.TransformerFlag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClassTransformers implements IClassTransformerHistory {

    private static final Logger LOGGER = Logger.getLogger(ClassTransformers.class.getName());
    private static final boolean DEBUG_CLASS_TRANSFORMING = Boolean.getBoolean("DEBUG_CLASS_TRANSFORMING");

    record TransformerHistoryEntry(Class<?> transformer, String transformerName, TransformerFlag transformerFlag, byte[] classData)
            implements ITransformerResultHistory {}

    private final List<IClassTransformer> transformers = new CopyOnWriteArrayList<>();
    private final Map<String, List<ITransformerResultHistory>> transformerHistoryCache = DEBUG_CLASS_TRANSFORMING ? new HashMap<>() : null;

    ClassTransformers() {}

    void add(IClassTransformer transformer) {
        transformers.add(transformer);
        LOGGER.log(Level.INFO, "Added transformer: {0}", transformer.getName());
    }

    byte[] transform(String name, byte[] classData) {
        if (transformers.isEmpty()) {
            LOGGER.log(Level.FINE, "No transformers registered for class: {0}", name);
            return null;
        }

        for (IClassTransformer transformer : transformers) {

            TransformResult result = transformer.transform(name, classData);

            if (DEBUG_CLASS_TRANSFORMING && transformerHistoryCache != null) {
                transformerHistoryCache.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>())
                        .add(new TransformerHistoryEntry(transformer.getClass(), transformer.getName(), result.flag(), result.classData()));
                LOGGER.log(Level.FINE, "Transformer history recorded for {0} using {1}", new Object[]{name, transformer.getName()});
            }

            if (result.flag() != TransformerFlag.NO_REWRITE) {
                LOGGER.log(Level.INFO, "Class {0} transformed by {1}", new Object[]{name, transformer.getName()});
                return result.classData();
            }
        }

        LOGGER.log(Level.FINE, "No transformation applied to class: {0}", name);

        return null;
    }

    @Override
    public List<ITransformerResultHistory> getHistory(String className) {
        if (!DEBUG_CLASS_TRANSFORMING || transformerHistoryCache == null) {
            return List.of();
        }
        return transformerHistoryCache.getOrDefault(className, List.of());
    }
}
