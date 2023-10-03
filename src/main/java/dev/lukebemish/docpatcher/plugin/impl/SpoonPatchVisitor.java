package dev.lukebemish.docpatcher.plugin.impl;

import net.neoforged.javadoctor.injector.spoon.JVMSignatureBuilder;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.JavadocEntry;
import spoon.reflect.declaration.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpoonPatchVisitor {
    public ClassJavadoc visit(CtType<?> clean, ClassJavadoc original) {
        Map<String, JavadocEntry> methods = new HashMap<>();
        Map<String, JavadocEntry> fields = new HashMap<>();

        Set<String> cleanMethods = Stream.concat(clean.getMethods().stream(), (clean instanceof CtClass<?> ctClass) ? ctClass.getConstructors().stream() : Stream.<CtExecutable<?>>of()).map(exec -> {
            final boolean isCtor = exec instanceof CtConstructor<?>;
            return (isCtor ? "<init>" : exec.getSimpleName()) + JVMSignatureBuilder.getJvmMethodSignature(exec);
        }).collect(Collectors.toSet());

        Set<String> cleanFields = clean.getFields().stream().map(CtField::getSimpleName).collect(Collectors.toSet());

        if (original.fields() != null) {
            for (var entry : original.fields().entrySet()) {
                if (!cleanFields.contains(entry.getKey())) {
                    fields.put(entry.getKey(), entry.getValue());
                }
            }
        }

        if (original.methods() != null) {
            for (var entry : original.methods().entrySet()) {
                if (!cleanMethods.contains(entry.getKey())) {
                    methods.put(entry.getKey(), entry.getValue());
                }
            }
        }

        Map<String, CtType<?>> cleanInnerClasses = clean.getNestedTypes().stream().filter(t -> !t.isAnonymous() && !t.isLocalType()).collect(Collectors.toMap(CtType::getSimpleName, Function.identity()));

        Map<String, ClassJavadoc> innerClasses = new HashMap<>();

        if (original.innerClasses() != null) {
            for (var entry : original.innerClasses().entrySet()) {
                if (!cleanInnerClasses.containsKey(entry.getKey())) {
                    innerClasses.put(entry.getKey(), entry.getValue());
                } else {
                    innerClasses.put(entry.getKey(), visit(cleanInnerClasses.get(entry.getKey()), entry.getValue()));
                }
            }
        }

        if (methods.isEmpty() && fields.isEmpty() && innerClasses.isEmpty()) {
            return null;
        }

        return new ClassJavadoc(null, methods.isEmpty() ? null : methods, fields.isEmpty() ? null : fields, innerClasses.isEmpty() ? null : innerClasses);
    }
}
