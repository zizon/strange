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

    protected ClassNode clazz;

    public RefinedClass(InputStream stream) {
        this.clazz = newClassNode(stream);
    }

    public RefinedClass revert() {
        this.clazz = Optional.ofNullable(clazz.attrs)
                .map((attrs) -> attrs.stream()
                        .filter((attr) -> CLASS_REFINED_MARK.equals(attr.type))
                        .findAny()
                        .orElse(null)
                )
                .map(UserDefinedAttribute.class::cast)
                .map(UserDefinedAttribute::content)
                .map(ByteArrayInputStream::new)
                .map(this::newClassNode)
                .orElse(this.clazz);

        return this;
    }

    public RefinedClass annotate() {
        if (clazz.attrs == null || clazz.attrs.stream().noneMatch((attribute) -> CLASS_REFINED_MARK.equals(attribute.type))) {
            List<Attribute> class_attributes = Optional.ofNullable(clazz.attrs)
                    .orElseGet(ArrayList::new);
            // copy bytecode
            ClassWriter writer = new ClassWriter(Opcodes.ASM7);
            clazz.accept(writer);
            byte[] bytecode = writer.toByteArray();
            class_attributes.add(new UserDefinedAttribute(CLASS_REFINED_MARK, bytecode));

            // update attrs
            clazz.attrs = class_attributes;
        }

        return this;
    }

    public byte[] bytecode() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        clazz.accept(writer);
        return writer.toByteArray();
    }

    public Stream<MethodInsnNode> profileMethod(Method method) {
        // annotate class
        this.annotate();

        return Optional.ofNullable(this.clazz.methods)
                .orElseGet(Collections::emptyList)
                .stream()
                // find right method
                .filter((node) -> node.desc.equals(Type.getType(method).getInternalName()))
                // preserved
                .peek((node) -> node.attrs = Optional.ofNullable(node.attrs)
                        .orElseGet(LinkedList::new))
                // not yet mark
                .filter((node) -> node.attrs.stream()
                        .noneMatch((attr) -> attr.type.equals(METHOD_REFINED_MARK)))
                // mark
                .peek((node) -> node.attrs.add(new UserDefinedAttribute(METHOD_REFINED_MARK, new byte[0])))
                .flatMap(this::refinedMethodNode)
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

    protected Stream<MethodInsnNode> refinedMethodNode(MethodNode method) {
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
        String signature = String.format("%s#%s;%s", this.clazz.name, method.name, method.desc);
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
                .forEach((return_point) -> {
                    instructions.insertBefore(return_point, generateLeaveInstruction(signature));
                });

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

        // scan methods
        return Arrays.stream(instructions.toArray())
                .filter((instruction) -> instruction instanceof MethodInsnNode)
                .map(MethodInsnNode.class::cast)
                .filter((node) -> !node.owner.equals(Type.getInternalName(Bridge.class)))
                .collect(Collectors.groupingBy(
                        (node) -> String.format("%s#%s;%s", node.owner, node.name, node.desc),
                        Collectors.reducing((left, right) -> left)
                ))
                .values()
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                ;
    }

    protected ClassNode newClassNode(InputStream bytecode) {
        try {
            ClassReader reader = new ClassReader(bytecode);
            ClassNode clazz = new ClassNode(Opcodes.ASM7);
            reader.accept(clazz, 0);
            return clazz;
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("fail to read bytecode from stream:%s", bytecode), e);
        }
    }
}
