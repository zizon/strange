package com.sf.doctor;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.StreamSupport;

public class RefinedClass {

    protected static final String CLASS_REFINED_MARK = "class_refiend_mark";

    public static String signature(ClassNode clazz, MethodNode method) {
        return String.format("%s -> %s; %s", clazz.name, method.name, method.desc);
    }

    public static String signature(Method method) {
        return String.format("%s -> %s; %s",
                Type.getInternalName(method.getDeclaringClass()),
                method.getName(),
                Type.getMethodDescriptor(method)
        );
    }

    protected ClassNode clazz;

    public RefinedClass(InputStream stream) {
        this.clazz = newClassNode(stream);
    }

    public RefinedClass revert() {
        this.clazz = Optional.ofNullable(clazz.attrs)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter((attr) -> CLASS_REFINED_MARK.equals(attr.type))
                .map(UserDefinedAttribute::content)
                .findAny()
                .map(ByteArrayInputStream::new)
                .map(this::newClassNode)
                .orElse(this.clazz);

        return this;
    }

    public RefinedClass profiling() {
        if (isAnnotated()) {
            return this;
        }

        this.annotate();

        Optional.ofNullable(this.clazz.methods)
                .orElseGet(Collections::emptyList)
                .forEach(this::refinedMethodNode);
        return this;
    }

    public byte[] bytecode() {
        ClassWriter writer = new ClassWriter(0
                | ClassWriter.COMPUTE_MAXS
                | ClassWriter.COMPUTE_FRAMES
        );
        clazz.accept(writer);
        return writer.toByteArray();
    }

    public RefinedClass print(PrintWriter output) {
        this.clazz.accept(new TraceClassVisitor(output));
        return this;
    }

    protected void annotate() {
        if (isAnnotated()) {
            return;
        }

        // copy bytecode
        ClassWriter writer = new ClassWriter(Opcodes.ASM7);
        clazz.accept(writer);
        byte[] bytecode = writer.toByteArray();
        if (clazz.attrs == null) {
            clazz.attrs = new LinkedList<>();
        }
        clazz.attrs.add(new UserDefinedAttribute(CLASS_REFINED_MARK, bytecode));
    }

    protected boolean isAnnotated() {
        return Optional.ofNullable(clazz.attrs)
                .orElseGet(LinkedList::new)
                .stream()
                .anyMatch((attribute) -> CLASS_REFINED_MARK.equals(attribute.type));
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

    protected void refinedMethodNode(MethodNode method) {
        if ((method.access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }

        Label start = new Label();
        Label end = new Label();

        // add start and end label
        InsnList instructions = new InsnList();
        LabelNode start_instruction = new LabelNode(start);
        LabelNode end_instruction = new LabelNode(end);
        instructions.add(start_instruction);
        instructions.add(method.instructions);
        instructions.add(end_instruction);

        // insert enter
        String signature = RefinedClass.signature(this.clazz, method);
        AbstractInsnNode entry_point = start_instruction;
        if (method.name.equals("<init>")) {
            // special case for init
            for (AbstractInsnNode super_init : instructions) {
                // check first method invocation
                if (super_init instanceof MethodInsnNode) {
                    // if is super init,delay entry point inject
                    if (super_init.getOpcode() == Opcodes.INVOKESPECIAL
                            && ((MethodInsnNode) super_init).name.equals("<init>")
                    ) {
                        entry_point = super_init;
                        break;
                    }

                    break;
                }
            }
        }
        instructions.insert(entry_point, generateEnterInstruction(signature));

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
                start_instruction,
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

        // scan methods
        return;
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
