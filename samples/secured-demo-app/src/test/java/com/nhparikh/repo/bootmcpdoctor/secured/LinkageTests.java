package com.nhparikh.repo.bootmcpdoctor.secured;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.FieldVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every class, field and method that boot-mcp-doctor's code uses is on this application's classpath, so nothing fails
 * to link on a code path that the other tests don't reach. This sample has every optional dependency that
 * boot-mcp-doctor uses, Spring Security included. References that only annotations make are left out, since the JVM
 * ignores missing annotation classes. Run on older Spring Boot versions, this finds the APIs boot-mcp-doctor needs that
 * they don't have yet; see docs/compatibility.md.
 */
class LinkageTests {

    private static final String PACKAGE = "com/nhparikh/repo/bootmcpdoctor/";

    private final ClassLoader classLoader = getClass().getClassLoader();

    private final Map<String, ClassInfo> classes = new HashMap<>();

    @Test
    void everyReferenceResolves() throws IOException {
        Set<String> checked = new TreeSet<>();
        List<Reference> references = new ArrayList<>();
        Resource[] resources = new PathMatchingResourcePatternResolver(this.classLoader)
            .getResources("classpath*:" + PACKAGE + "**/*.class");
        for (Resource resource : resources) {
            String name = resource.getURL().toString().replaceFirst(".*/(" + PACKAGE + ".*)\\.class$", "$1");
            if (!name.startsWith(PACKAGE + "secured/") && checked.add(name)) {
                try (InputStream in = resource.getInputStream()) {
                    new ClassReader(in).accept(new ReferenceCollector(name, references), ClassReader.SKIP_FRAMES);
                }
            }
        }
        assertThat(checked).as("boot-mcp-doctor classes").hasSizeGreaterThan(20);
        List<String> unresolved = references.stream()
            .filter((reference) -> !resolves(reference))
            .map(Reference::toString)
            .distinct()
            .toList();
        assertThat(unresolved).as("references that do not resolve on Spring Boot " + SpringBootVersion.getVersion())
            .isEmpty();
    }

    private boolean resolves(Reference reference) {
        // A method of an array, such as clone(), belongs to Object.
        String owner = reference.owner().startsWith("[") ? "java/lang/Object" : reference.owner();
        if (info(owner) == null) {
            return false;
        }
        return switch (reference.kind()) {
            case CLASS -> true;
            case FIELD -> find(owner, reference, ClassInfo::fields);
            case METHOD -> find(owner, reference, ClassInfo::methods);
        };
    }

    // Looks for the member in the class, its superclasses and its interfaces, as the JVM does when linking.
    private boolean find(String owner, Reference reference, Function<ClassInfo, Set<String>> members) {
        List<String> queue = new ArrayList<>(List.of(owner, "java/lang/Object"));
        Set<String> seen = new TreeSet<>();
        while (!queue.isEmpty()) {
            String name = queue.remove(0);
            ClassInfo info = seen.add(name) ? info(name) : null;
            if (info == null) {
                continue;
            }
            if (members.apply(info).contains(reference.name() + reference.descriptor())) {
                return true;
            }
            if (info.superName != null) {
                queue.add(info.superName);
            }
            queue.addAll(info.interfaces);
        }
        return false;
    }

    private ClassInfo info(String name) {
        return this.classes.computeIfAbsent(name, this::read);
    }

    private ClassInfo read(String name) {
        try (InputStream in = this.classLoader.getResourceAsStream(name + ".class")) {
            if (in == null) {
                return null;
            }
            ClassInfo info = new ClassInfo();
            new ClassReader(in).accept(info, ClassReader.SKIP_CODE);
            return info;
        }
        catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private enum Kind {

        CLASS, FIELD, METHOD

    }

    private record Reference(Kind kind, String owner, String name, String descriptor, String from) {

        @Override
        public String toString() {
            return this.kind + " " + this.owner + ((this.name != null) ? "." + this.name + this.descriptor : "")
                    + " (from " + this.from + ")";
        }
    }

    // The supertypes and members of a class, read from its class file without loading it.
    private static final class ClassInfo extends ClassVisitor {

        private final Set<String> fields = new TreeSet<>();

        private final Set<String> methods = new TreeSet<>();

        private final List<String> interfaces = new ArrayList<>();

        private String superName;

        ClassInfo() {
            super(Opcodes.ASM9);
        }

        Set<String> fields() {
            return this.fields;
        }

        Set<String> methods() {
            return this.methods;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            this.superName = superName;
            this.interfaces.addAll(List.of(interfaces));
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            this.fields.add(name + descriptor);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
            this.methods.add(name + descriptor);
            return null;
        }
    }

    // Collects what linking the class can need: its supertypes, the types in its signatures, and the classes, fields
    // and methods its code uses. Annotations and the InnerClasses attribute are left out, since neither makes the JVM
    // load a class.
    private static final class ReferenceCollector extends ClassVisitor {

        private final String from;

        private final List<Reference> references;

        ReferenceCollector(String from, List<Reference> references) {
            super(Opcodes.ASM9);
            this.from = from;
            this.references = references;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            if (superName != null) {
                type(superName);
            }
            for (String type : interfaces) {
                type(type);
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            type(Type.getType(descriptor));
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
            types(Type.getMethodType(descriptor));
            return new MethodVisitor(Opcodes.ASM9) {

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    type(Type.getObjectType(type));
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String field, String fieldDescriptor) {
                    add(Kind.FIELD, owner, field, fieldDescriptor);
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String method, String methodDescriptor,
                        boolean isInterface) {
                    add(Kind.METHOD, owner, method, methodDescriptor);
                }

                @Override
                public void visitInvokeDynamicInsn(String method, String methodDescriptor, Handle bootstrap,
                        Object... arguments) {
                    handle(bootstrap);
                    types(Type.getMethodType(methodDescriptor));
                    for (Object argument : arguments) {
                        if (argument instanceof Handle handle) {
                            handle(handle);
                        }
                        else if (argument instanceof Type type) {
                            constant(type);
                        }
                    }
                }

                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof Type type) {
                        constant(type);
                    }
                    else if (value instanceof Handle handle) {
                        handle(handle);
                    }
                }

                @Override
                public void visitMultiANewArrayInsn(String arrayDescriptor, int dimensions) {
                    type(Type.getType(arrayDescriptor));
                }

                @Override
                public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                    if (type != null) {
                        type(type);
                    }
                }
            };
        }

        private void handle(Handle handle) {
            Kind kind = (handle.getTag() <= Opcodes.H_PUTSTATIC) ? Kind.FIELD : Kind.METHOD;
            add(kind, handle.getOwner(), handle.getName(), handle.getDesc());
        }

        private void constant(Type type) {
            if (type.getSort() == Type.METHOD) {
                types(type);
            }
            else {
                type(type);
            }
        }

        private void types(Type methodType) {
            type(methodType.getReturnType());
            for (Type argument : methodType.getArgumentTypes()) {
                type(argument);
            }
        }

        private void type(Type type) {
            Type element = (type.getSort() == Type.ARRAY) ? type.getElementType() : type;
            if (element.getSort() == Type.OBJECT) {
                type(element.getInternalName());
            }
        }

        private void type(String internalName) {
            if (internalName.startsWith("[")) {
                type(Type.getType(internalName));
            }
            else {
                add(Kind.CLASS, internalName, null, null);
            }
        }

        private void add(Kind kind, String owner, String name, String descriptor) {
            this.references.add(new Reference(kind, owner, name, descriptor, this.from));
        }
    }
}
