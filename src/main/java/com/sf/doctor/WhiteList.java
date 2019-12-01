package com.sf.doctor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class WhiteList {

    public Set<String> buildSignatures() {
        return collectSignatureFromClass(WhiteList.class)
                .collect(Collectors.toSet());
    }

    protected Stream<String> collectSignatureFromClass(Class<?> root_locator) {
        try {
            Path source_root = Paths.get(
                    root_locator.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );

            // for plain class files
            if (Files.isDirectory(source_root)) {
                try (Stream<Path> walker = Files.walk(source_root)) {
                    return walker.filter(Files::isRegularFile)
                            .map((path) -> {
                                try {
                                    return streamToClass(path.toFile().toURI().toURL().openStream());
                                } catch (IOException e) {
                                    throw new UncheckedIOException(String.format(
                                            "fail to open file:%s",
                                            path
                                    ), e);
                                }
                            })
                            .flatMap(this::findReferenceMethodSignatures)
                            ;
                }
            } else {
                // should be a jar file
                JarFile jar = new JarFile(source_root.toFile());
                return fromJarFile(jar)
                        .filter((entry) -> entry.getName().endsWith(".class"))
                        .map((entry) -> {
                            try {
                                return streamToClass(jar.getInputStream(entry));
                            } catch (IOException e) {
                                throw new UncheckedIOException(String.format(
                                        "fail to open entry:%s",
                                        entry
                                ), e);
                            }
                        })
                        .flatMap(this::findReferenceMethodSignatures)
                        ;

            }
        } catch (URISyntaxException | IOException e) {
            throw new UncheckedIOException(
                    String.format("fail to locate root of class:%s", root_locator),
                    e instanceof IOException ? (IOException) e : new IOException(e)
            );
        }
    }

    protected ClassNode streamToClass(InputStream input) {
        ClassNode class_file = new ClassNode(Opcodes.ASM7);
        try (InputStream stream = input) {
            ClassReader reader = new ClassReader(stream);
            reader.accept(class_file, 0);
            return class_file;
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("fail to load class file of %s", input), e);
        }
    }

    protected Stream<String> findReferenceMethodSignatures(ClassNode class_node) {
        return Optional.ofNullable(class_node.methods)
                .orElseGet(Collections::emptyList)
                .stream()
                .flatMap((method) ->
                        Arrays.stream(
                                Optional.ofNullable(method.instructions)
                                        .map(InsnList::toArray)
                                        .orElseGet(() -> new AbstractInsnNode[0])
                        )
                                // filter and cast to method node
                                .filter((method_node) -> method_node instanceof MethodInsnNode)
                                .map(MethodInsnNode.class::cast)
                                .map((node) -> RefinedClass.signature(class_node, node))
                )
                ;
    }

    protected Stream<JarEntry> fromJarFile(JarFile file) {
        return StreamSupport.stream(((Iterable<JarEntry>) () -> new Iterator<JarEntry>() {
            Enumeration<JarEntry> enumeration = file.entries();

            @Override
            public boolean hasNext() {
                if (enumeration.hasMoreElements()) {
                    return true;
                }

                try {
                    file.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(String.format(
                            "fail to close jar:%s",
                            file
                    ), e);
                }
                return false;
            }

            @Override
            public JarEntry next() {
                return enumeration.nextElement();
            }
        }).spliterator(), false);
    }
}
