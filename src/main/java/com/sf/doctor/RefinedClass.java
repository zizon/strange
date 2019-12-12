package com.sf.doctor;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

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
    protected final byte[] provided;

    public RefinedClass(byte[] bytecode) {
        this.provided = bytecode;
        this.clazz = newClassNode(this.provided);
    }

    public byte[] revert() {
        return Optional.ofNullable(clazz.attrs)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter((attr) -> CLASS_REFINED_MARK.equals(attr.type))
                .map(UserDefinedAttribute::content)
                .findAny()
                .orElse(this.provided);
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
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
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
        if (clazz.attrs == null) {
            clazz.attrs = new LinkedList<>();
        }
        clazz.attrs.add(new UserDefinedAttribute(CLASS_REFINED_MARK, this.provided));
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
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return;
        }

        // signature
        String signature = RefinedClass.signature(this.clazz, method);

        Label start = new Label();
        Label end = new Label();

        // add start and end label
        InsnList instructions = new InsnList();
        LabelNode start_instruction = new LabelNode(start);
        LabelNode end_instruction = new LabelNode(end);

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

        // special case for init
        AbstractInsnNode insert_after = null;
        if (method.name.equals("<init>")) {
            // special case for init

            // 1.find this()
            for (AbstractInsnNode super_init : method.instructions) {
                // check first method invocation
                if (super_init instanceof MethodInsnNode) {
                    if (super_init.getOpcode() == Opcodes.INVOKESPECIAL
                            && ((MethodInsnNode) super_init).name.equals("<init>")
                            && ((MethodInsnNode) super_init).owner.equals(clazz.name)) {
                        insert_after = super_init;
                    }
                }
            }

            // 2. find super()
            if (insert_after == null) {
                for (AbstractInsnNode super_init : method.instructions) {
                    // check first method invocation
                    if (super_init instanceof MethodInsnNode) {
                        if (super_init.getOpcode() == Opcodes.INVOKESPECIAL
                                && ((MethodInsnNode) super_init).name.equals("<init>")
                                && ((MethodInsnNode) super_init).owner.equals(clazz.superName)) {
                            insert_after = super_init;
                        }
                    }
                }
            }
        }

        if (insert_after != null) {
            method.instructions.insert(insert_after, start_instruction);
            method.instructions.insert(start_instruction, generateEnterInstruction(signature));
        } else {
            instructions.add(start_instruction);
            instructions.add(generateEnterInstruction(signature));
        }
        instructions.add(method.instructions);
        instructions.add(end_instruction);
        instructions.add(generateLeaveInstruction(signature));
        instructions.add(new InsnNode(Opcodes.ATHROW));

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

        // replace
        method.instructions = instructions;

        // scan methods
        return;
    }

    protected ClassNode newClassNode(byte[] bytecode) {
        ClassReader reader = new ClassReader(bytecode);
        ClassNode clazz = new ClassNode(Opcodes.ASM7);
        reader.accept(clazz, 0);
        return clazz;
    }
}
