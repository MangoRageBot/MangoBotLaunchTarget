package org.mangorage.mangobotlaunch.launch;

import org.mangorage.bootstrap.api.logging.IDeferredMangoLogger;
import org.mangorage.bootstrap.api.logging.ILoggerFactory;
import org.mangorage.bootstrap.api.transformer.IClassTransformer;
import org.mangorage.bootstrap.api.transformer.IClassTransformerHistory;
import org.mangorage.bootstrap.api.transformer.ITransformerResultHistory;
import org.mangorage.bootstrap.api.transformer.TransformResult;
import org.mangorage.bootstrap.api.transformer.TransformerFlag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClassTransformers implements IClassTransformerHistory {

    private static final IDeferredMangoLogger LOGGER = ILoggerFactory.getDefault().getWrappedProvider("slf4j", ClassTransformers.class);
    private static final boolean DEBUG_CLASS_TRANSFORMING = Boolean.getBoolean("DEBUG_CLASS_TRANSFORMING");

    record TransformerHistoryEntry(Class<?> transformer, String transformerName, TransformerFlag transformerFlag, byte[] classData, byte[] transformerResult, ITransformerResultHistory previous)
            implements ITransformerResultHistory {}

    private final List<IClassTransformer> transformers = new CopyOnWriteArrayList<>();
    private final Map<String, List<ITransformerResultHistory>> transformerHistoryCache = DEBUG_CLASS_TRANSFORMING ? new HashMap<>() : null;

    ClassTransformers() {}

    void add(IClassTransformer transformer) {
        transformers.add(transformer);
        LOGGER.get().info("Added transformer: {0}", transformer.getName());
    }

    byte[] transform(String name, byte[] classData) {
        if (name == null || name.contains("MangoLogger") || name.contains("slf4j")) {
            System.out.printf("Skipping transformation for %s to avoid potential logging issues%n", name);
            return classData;
        }


        final var logger = LOGGER.get(); // single reference at the top

        if (transformers.isEmpty()) {
            logger.info("No transformers registered for class: {0}", name);
            return classData;
        }

        ITransformerResultHistory previous = null;
        List<ITransformerResultHistory> historyList = DEBUG_CLASS_TRANSFORMING
                ? transformerHistoryCache.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>())
                : null;

        for (IClassTransformer transformer : transformers) {
            TransformResult result = transformer.transform(name, classData);

            if (DEBUG_CLASS_TRANSFORMING) {
                TransformerHistoryEntry entry = new TransformerHistoryEntry(
                        transformer.getClass(),
                        transformer.getName(),
                        result.flag(),
                        classData,
                        result.classData(),
                        previous
                );

                historyList.add(entry);
                previous = entry;

                logger.info("Transformer history recorded for {0} using {1}",
                        new Object[]{name, transformer.getName()});
            }

            if (result.flag() != TransformerFlag.NO_REWRITE) {
                logger.info("Class {0} transformed by {1}", new Object[]{name, transformer.getName()});
                return result.classData();
            }
        }

        logger.info("No transformation applied to class: {0}", name);
        return classData;
    }



    @Override
    public List<ITransformerResultHistory> getHistory(String className) {
        if (!DEBUG_CLASS_TRANSFORMING || transformerHistoryCache == null) {
            return List.of();
        }
        return transformerHistoryCache.getOrDefault(className, List.of());
    }
}
