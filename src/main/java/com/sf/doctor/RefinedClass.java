package com.sf.doctor;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RefinedClass {

    protected static final String CLASS_REFINED_MARK = "class_refiend_mark";
    protected static final String METHOD_REFINED_MARK = "class_refiend_mark";

    public static RefinedClass loadLoadableClass(Class<?> clazz) {
        try (InputStream stream = new URL(
                clazz.getProtectionDomain().getCodeSource().getLocation(),
                clazz.getName().replace(".", "/") + ".class").openStream()) {
            return new RefinedClass(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("code not found:%s", clazz), e);
        }
    }

    public static String signature(ClassNode clazz, MethodNode method) {
        return String.format("%s -> %s; %s", clazz.name, method.name, method.desc);
    }

    public static String signature(MethodInsnNode method) {
        return String.format("%s -> %s; %s", method.owner, method.name, method.desc);
    }

    public static String signature(Method method) {
        return String.format("%s -> %s; %s",
                Type.getInternalName(method.getDeclaringClass()),
                method.getName(),
                Type.getMethodDescriptor(method)
        );
    }

    protected ClassNode clazz;

    public RefinedClass(Class<?> clazz) {
        this.clazz = new ClassNode();
        try {
            ClassReader reader = new ClassReader(clazz.getName());
            reader.accept(this.clazz, 0);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format(
                    "fail to read bytecode of:%s",
                    clazz
            ), e);
        }
    }

    public RefinedClass(InputStream stream) {
        this.clazz = newClassNode(stream);
    }

    public RefinedClass revert() {
        this.clazz = Optional.ofNullable(clazz.attrs)
                .flatMap(attrs -> attrs.stream()
                        .filter((attr) -> CLASS_REFINED_MARK.equals(attr.type))
                        .findAny())
                .map(UserDefinedAttribute.class::cast)
                .map(UserDefinedAttribute::content)
                .map(ByteArrayInputStream::new)
                .map(this::newClassNode)
                .orElse(this.clazz);

        return this;
    }

    public RefinedClass annotate() {
        clazz.attrs = Optional.ofNullable(clazz.attrs).orElseGet(LinkedList::new);

        if (clazz.attrs.stream()
                .noneMatch((attribute) -> CLASS_REFINED_MARK.equals(attribute.type))) {
            // copy bytecode
            ClassWriter writer = new ClassWriter(Opcodes.ASM7);
            clazz.accept(writer);
            byte[] bytecode = writer.toByteArray();
            clazz.attrs.add(new UserDefinedAttribute(CLASS_REFINED_MARK, bytecode));
        }

        return this;
    }

    public byte[] bytecode() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        clazz.accept(writer);
        return writer.toByteArray();
    }

    public MethodNode profileMethod(Method method) {
        // annotate class
        this.annotate();

        return Optional.ofNullable(this.clazz.methods)
                .orElseGet(Collections::emptyList)
                .stream()
                // find right method
                .filter((node) -> node.name.equals(method.getName()))
                .filter((node) -> node.desc.equals(Type.getMethodDescriptor(method)))
                // refine
                .map(this::refinedMethodNode)
                .findAny()
                .orElseThrow(() -> new RuntimeException(String.format("fail to profile method: %s", RefinedClass.signature(method))))
                ;
    }

    public void print(PrintWriter output) {
        this.clazz.accept(new TraceClassVisitor(output));
    }

    protected InsnList generateEnterInstruction(String signature) {
        InsnList enter = new InsnList();
        enter.add(new LdcInsnNode(signature));
        enter.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(Bridge.class),
                "enter",
                Type.getMethodDescriptor(
                        Type.getType(void.class),
                        Type.getType(String.class)
                )
        ));
        return enter;
    }

    protected InsnList generateLeaveInstruction(String signature) {
        InsnList enter = new InsnList();
        enter.add(new LdcInsnNode(signature));
        enter.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(Bridge.class),
                "leave",
                Type.getMethodDescriptor(
                        Type.getType(void.class),
                        Type.getType(String.class)
                )
        ));
        return enter;
    }

    protected MethodNode refinedMethodNode(MethodNode method) {
        method.attrs = Optional.ofNullable(method.attrs)
                .orElseGet(LinkedList::new);
        if (method.attrs.stream().anyMatch((attr) -> attr.type.equals(METHOD_REFINED_MARK))) {
            return method;
        }

        Label start = new Label();
        Label end = new Label();

        // add start and end label
        InsnList instructions = new InsnList();
        LabelNode start_instuction = new LabelNode(start);
        LabelNode end_instruction = new LabelNode(end);
        instructions.add(start_instuction);
        instructions.add(method.instructions);
        instructions.add(end_instruction);

        // insert enter
        String signature = RefinedClass.signature(this.clazz, method);
        instructions.insert(start_instuction, generateEnterInstruction(signature));

        // insert on return/throw point
        Arrays.stream(instructions.toArray())
                .filter((instruction) -> {
                    switch (instruction.getOpcode()) {
                        case Opcodes.IRETURN:
                        case Opcodes.LRETURN:
                        case Opcodes.FRETURN:
                        case Opcodes.DRETURN:
                        case Opcodes.ARETURN:
                        case Opcodes.RETURN:
                        case Opcodes.ATHROW:
                            return true;
                    }

                    return false;
                })
                .forEach((return_point) -> instructions.insertBefore(return_point, generateLeaveInstruction(signature)));

        // add a generated try-catch-all block
        List<TryCatchBlockNode> try_catches = Optional.ofNullable(method.tryCatchBlocks)
                .orElseGet(LinkedList::new);
        try_catches.add(new TryCatchBlockNode(
                start_instuction,
                end_instruction,
                end_instruction,
                Type.getInternalName(Throwable.class)
        ));
        method.tryCatchBlocks = try_catches;

        // try catch all handler
        InsnList catch_all = generateLeaveInstruction(signature);
        catch_all.add(new InsnNode(Opcodes.ATHROW));
        instructions.insert(end_instruction, catch_all);

        // replace
        method.instructions = instructions;

        // add mark
        method.attrs.add(new UserDefinedAttribute(METHOD_REFINED_MARK, new byte[0]));

        // scan methods
        return method;
    }

    protected ClassNode newClassNode(InputStream bytecode) {
        try (InputStream stream = bytecode) {
            ClassReader reader = new ClassReader(stream);
            ClassNode clazz = new ClassNode(Opcodes.ASM7);
            reader.accept(clazz, 0);
            return clazz;
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("fail to read bytecode from stream:%s", bytecode), e);
        }
    }
}
