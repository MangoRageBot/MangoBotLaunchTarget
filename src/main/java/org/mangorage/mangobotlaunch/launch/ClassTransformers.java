package org.mangorage.mangobotlaunch.launch;

import org.mangorage.bootstrap.api.transformer.IClassTransformer;
import org.mangorage.bootstrap.api.transformer.TransformResult;
import org.mangorage.bootstrap.api.transformer.TransformerFlag;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClassTransformers {

    private static final boolean DEBUG_CLASS_TRANSFORMING = Boolean.getBoolean("DEBUG_CLASS_TRANSFORMING");


    private final List<IClassTransformer> transformers = new CopyOnWriteArrayList<>();


    ClassTransformers() {}

    void add(IClassTransformer transformer) {
        transformers.add(transformer);
    }

    byte[] transform(String name, byte[] classData) {

        for (IClassTransformer transformer : transformers) {

            TransformResult result = transformer.transform(name, classData);

            if (DEBUG_CLASS_TRANSFORMING) {
                // TODO: Implement this
            }

            if (result.flag() != TransformerFlag.NO_REWRITE) {
                System.out.println(
                        "%s Transformed %s"
                                .formatted(transformer.getName(), name)
                );

                return result.classData();
            }
        }

        return null;
    }

}
