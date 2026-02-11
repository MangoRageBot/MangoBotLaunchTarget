package org.mangorage.mangobotlaunch.launch;

import org.mangorage.bootstrap.api.logging.ILoggerFactory;
import org.mangorage.bootstrap.api.logging.IMangoLogger;
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

    private static final IMangoLogger LOGGER = ILoggerFactory.getDefault().getWrappedProvider("slf4j").getLogger(ClassTransformers.class);
    private static final boolean DEBUG_CLASS_TRANSFORMING = Boolean.getBoolean("DEBUG_CLASS_TRANSFORMING");

    record TransformerHistoryEntry(Class<?> transformer, String transformerName, TransformerFlag transformerFlag, byte[] classData, byte[] transformerResult, ITransformerResultHistory previous)
            implements ITransformerResultHistory {}

    private final List<IClassTransformer> transformers = new CopyOnWriteArrayList<>();
    private final Map<String, List<ITransformerResultHistory>> transformerHistoryCache = DEBUG_CLASS_TRANSFORMING ? new HashMap<>() : null;

    ClassTransformers() {}

    void add(IClassTransformer transformer) {
        transformers.add(transformer);
        LOGGER.info("Added transformer: {0}", transformer.getName());
    }

    byte[] transform(String name, byte[] classData) {
        if (transformers.isEmpty()) {
            LOGGER.info("No transformers registered for class: {0}", name);
            return null;
        }

        ITransformerResultHistory previous = null;
        List<ITransformerResultHistory> historyList = DEBUG_CLASS_TRANSFORMING ? transformerHistoryCache.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()) : null;

        for (IClassTransformer transformer : transformers) {

            TransformResult result = transformer.transform(name, classData);

            if (DEBUG_CLASS_TRANSFORMING) {

                // create and store the history entry
                TransformerHistoryEntry entry = new TransformerHistoryEntry(
                        transformer.getClass(),
                        transformer.getName(),
                        result.flag(),
                        classData,         // original data before this transformer
                        result.classData(),  // result of this transformer
                        previous             // previous history entry
                );

                historyList.add(entry);
                previous = entry; // update previous for the next iteration

                LOGGER.info("Transformer history recorded for {0} using {1}", new Object[]{name, transformer.getName()});
            }

            if (result.flag() != TransformerFlag.NO_REWRITE) {
                LOGGER.info("Class {0} transformed by {1}", new Object[]{name, transformer.getName()});
                return result.classData();
            }

            // currentData remains the same if NO_REWRITE
        }

        LOGGER.info("No transformation applied to class: {0}", name);
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
