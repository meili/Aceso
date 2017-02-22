/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mogujie.instantrun;


import com.mogujie.aceso.util.Log;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;

import java.util.*;


public class IncrementalChangeVisitor extends IncrementalVisitor {

    public static final VisitorBuilder VISITOR_BUILDER = new IncrementalVisitor.VisitorBuilder() {

        @Override
        public IncrementalVisitor build(ClassNode classNode,
                                        List<ClassNode> parentNodes,
                                        ClassVisitor classVisitor) {
            return new IncrementalChangeVisitor(classNode, parentNodes, classVisitor);
        }


        @Override
        public String getMangledRelativeClassFilePath(String path) {
            // Remove .class (length 6) and replace with $override.class
            return path.substring(0, path.length() - 6) + OVERRIDE_SUFFIX + ".class";
        }


        @Override
        public OutputType getOutputType() {
            return OutputType.OVERRIDE;
        }
    };

    public static final String OVERRIDE_SUFFIX = "$override";

    private static final String METHOD_MANGLE_PREFIX = "static$";

    private MachineState state = MachineState.NORMAL;
    private boolean instantRunDisabled = false;

    // Description prefix used to add fake "this" as the first argument to each instance method
    // when converted to a static method.
    private String instanceToStaticDescPrefix;

    // List of constructors we encountered and deconstructed.
    List<MethodNode> addedMethods = new ArrayList();

    ArrayList<String> fixMtds = new ArrayList<String>();

    //被调用到的super.xxx()方法
    ArrayList<InstantMethod> superMethods = new ArrayList<InstantMethod>();

    private enum MachineState {
        NORMAL, AFTER_NEW
    }

    HashSet<String> priNativeMtdSet = new HashSet<String>();
    HashSet<String> priSyncMtdSet = new HashSet<String>();
    List<MethodNode> methodNodes = null;

    public IncrementalChangeVisitor(
            ClassNode classNode,
            List<ClassNode> parentNodes,
            ClassVisitor classVisitor) {
        super(classNode, parentNodes, classVisitor);
        methodNodes = classNode.methods;
        for (int i = 0; i < classNode.methods.size(); i++) {
            MethodNode methodNode = (MethodNode) classNode.methods.get(i);
            if ((methodNode.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_PRIVATE)) == (Opcodes.ACC_NATIVE | Opcodes.ACC_PRIVATE)) {
                priNativeMtdSet.add(methodNode.name + "." + methodNode.desc);
                System.out.println("find private native mtd : " + methodNode.name + "." + methodNode.desc);
            }
            if ((methodNode.access & (Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_PRIVATE)) == (Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_PRIVATE)) {
                priSyncMtdSet.add(methodNode.name + "." + methodNode.desc);
                System.out.println("find private sync mtd : " + methodNode.name + "." + methodNode.desc);
            }
        }

    }

    /**
     * Turns this class into an override class that can be loaded by our custom class loader:
     * <ul>
     * <li>Make the class name be OriginalName$override</li>
     * <li>Ensure the class derives from java.lang.Object, no other inheritance</li>
     * <li>Ensure the class has a public parameterless constructor that is a noop.</li>
     * </ul>
     */
    @Override
    public void visit(int version, int access, String name, String signature, String superName,
                      String[] interfaces) {
        super.visit(version, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                name + OVERRIDE_SUFFIX, signature, "java/lang/Object",
                new String[]{CHANGE_TYPE.getInternalName()});

        visitedClassName = name;
        visitedSuperName = superName;
        instanceToStaticDescPrefix = "(L" + visitedClassName + ";";

        // Create empty constructor
        MethodVisitor mv = super
                .visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V",
                false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

//        super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_STATIC,
//                "$obsolete", "Z", null, null);
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc) {
        // Ignore, the class hierarchy is not relevant in the override classes.
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        // Ignore, the class hierarchy is not relevant in the override classes.
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (DISABLE_ANNOTATION_TYPE.getDescriptor().equals(desc)) {
            instantRunDisabled = true;
        }
        return super.visitAnnotation(desc, visible);
    }


    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                     String[] exceptions) {

        if (instantRunDisabled || !isAccessCompatibleWithInstantRun(access)) {
            // Nothing to generate.
            return null;
        }
        if (name.equals("<clinit>")) {
            // we skip the class init as it can reset static fields which we don't support right now
            return null;
        }
        Log.v("visit method " + name + "  " + desc);
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        boolean isPrivate = (access & Opcodes.ACC_PRIVATE) != 0;
        boolean isSync = (access & Opcodes.ACC_SYNCHRONIZED) != 0;

        String newDesc = computeOverrideMethodDesc(desc, isStatic);

        // Do not carry on any access flags from the original method. For example synchronized
        // on the original method would translate into a static synchronized method here.
        access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;

//        MethodNode method = getMethodByNameInClass(name, desc, classNode);
        if (name.equals("<init>")) {
            return null;
        } else {
            String newName = isStatic ? computeOverrideMethodName(name, desc) : name;
            MethodVisitor original = super.visitMethod(access, newName, newDesc, signature, exceptions);
            for (MethodNode methodNode : methodNodes) {
                if (desc.equals(methodNode.desc) && name.equals(methodNode.name)) {
                    processNew(methodNode);
                }
            }

            return new ISVisitor(original, access, newName, newDesc,
                    IncrementalTool.getMtdSig(name, desc), isStatic, false /* isConstructor */);
        }
    }

    private void processNew(MethodNode methodNode) {
        int size = methodNode.instructions.size();
        List<AbstractInsnNode> removeList = new ArrayList();
        for (int i = 0; i < size; i++) {
            insnMachine(methodNode.instructions.get(i), removeList);
        }
        if (removeList.size() % 2 != 0) {
            throw new RuntimeException("some error in remove new and dup ins");
        }
        for (AbstractInsnNode removeNode : removeList) {
            methodNode.instructions.remove(removeNode);
        }
    }

    public static final int INIT = 0;
    public static final int FIND_NEW = 1;
    public static final int FIND_NEW_AND_DUP = 2;
    private int status = INIT;

    private void insnMachine(AbstractInsnNode node, List<AbstractInsnNode> removeList) {
        if (node.getOpcode() == Opcodes.NEW) {
            if (status == INIT) {
                status = FIND_NEW;
                removeList.add(node);
            } else {
                throw new RuntimeException("find new in status " + status);
            }
        }

        if (node.getOpcode() == Opcodes.DUP) {
            if (status == FIND_NEW) {
                status = INIT;
                removeList.add(node);
            }
        }

        if (node.getOpcode() == Opcodes.INVOKESPECIAL
                && node instanceof MethodInsnNode) {
            if (status == INIT) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) node;
                if ("<init>".equals(methodInsnNode.name)) {
                    if (getMethodAccessRight(methodInsnNode.owner, methodInsnNode.name
                            , methodInsnNode.desc) == AccessRight.PUBLIC) {
                        int removeSize = removeList.size();
                        removeList.remove(removeSize - 1);
                        removeList.remove(removeSize - 2);
                    }
                }

            }
        }


    }

    /**
     * Returns the actual method access right or a best guess if we don't have access to the
     * method definition.
     *
     * @param owner the method owner class
     * @param name  the method name
     * @param desc  the method signature
     * @return the {@link AccessRight} for that method.
     */
    private AccessRight getMethodAccessRight(String owner, String name, String desc) {
        AccessRight accessRight;
        if (owner.equals(visitedClassName)) {
            MethodNode methodByName = getMethodByName(name, desc);
            if (methodByName == null) {
                // we did not find the method invoked on ourselves, which mean that it really
                // is a parent class method invocation and we just don't have access to it.
                // the most restrictive access right in that case is protected.
                return AccessRight.PROTECTED;
            }
            accessRight = AccessRight.fromNodeAccess(methodByName.access);
        } else {
            // we are accessing another class method, and since we make all protected and
            // package-private methods public, we can safely assume it is public.
            accessRight = AccessRight.PUBLIC;
        }
        return accessRight;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
                                   Object value) {
        // do not add any of the original class fields in the $override class, they would never
        // be used and confuse the debugger.
        return null;
    }

    public class ISVisitor extends GeneratorAdapter {

        private final boolean isStatic;
        private final boolean isConstructor;
        private final String originalMtdSig;

        /**
         * Instrument a method.
         *
         * @param mv            the parent method visitor.
         * @param access        the method access flags.
         * @param name          method name.
         * @param desc          method signature.
         * @param isStatic      true if the instrumented method was originally a static method.
         * @param isConstructor true if  the instrumented code was originally a constructor body.
         */
        public ISVisitor(
                MethodVisitor mv,
                int access,
                String name,
                String desc,
                String originalMtdSig,
                boolean isStatic,
                boolean isConstructor) {
            super(Opcodes.ASM5, mv, access, name, desc);
            this.isStatic = isStatic;
            this.isConstructor = isConstructor;
            this.originalMtdSig = originalMtdSig;
            if (!IncrementalTool.isMethodLevelFix()) {
                fixMtds.add(originalMtdSig);
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {

            if (IncrementalTool.isMethodLevelFix()
                    && desc.equals(FIXMTD_ANNOTATION_TYPE.getDescriptor())) {
                fixMtds.add(originalMtdSig);
            }
            return super.visitAnnotation(desc, visible);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {

            AccessRight accessRight;
            if (!owner.equals(visitedClassName)) {
                // we are accessing another object field, and at this point the visitor is not smart
                // enough to know if has seen this class before or not so we must assume the field
                // is *not* accessible from the $override class which lives in a different
                // hierarchy and package.
                // However, since we made all package-private and protected fields public, and it
                // cannot be private since the visitedClassName is not the "owner", we can safely
                // assume it's public.
                accessRight = AccessRight.PUBLIC;
            } else {
                // check the field access bits.
                FieldNode fieldNode = getFieldByName(name);
                if (fieldNode == null) {
                    // If this is an inherited field, we might not have had access to the parent
                    // bytecode. In such a case, treat it as private.
                    accessRight = AccessRight.PACKAGE_PRIVATE;
                } else {
                    accessRight = AccessRight.fromNodeAccess(fieldNode.access);
                }
            }

            boolean handled = false;
            switch (opcode) {
                case Opcodes.PUTSTATIC:
                case Opcodes.GETSTATIC:
                    handled = visitStaticFieldAccess(opcode, owner, name, desc, accessRight);
                    break;
                case Opcodes.PUTFIELD:
                case Opcodes.GETFIELD:
                    handled = visitFieldAccess(opcode, owner, name, desc, accessRight);
                    break;
                default:
                    System.out.println("Unhandled field opcode " + opcode);
            }
            if (!handled) {
                super.visitFieldInsn(opcode, owner, name, desc);
            }
        }

        /**
         * Visits an instance field access. The field could be of the visited class or it could be
         * an accessible field from the class being visited (unless it's private).
         * <p/>
         * For private instance fields, the access instruction is rewritten to calls to reflection
         * to access the fields value:
         * <p/>
         * Pseudo code for Get:
         * <code>
         * value = $instance.fieldName;
         * </code>
         * becomes:
         * <code>
         * value = (unbox)$package/AndroidInstantRuntime.getPrivateField($instance, $fieldName);
         * </code>
         * <p/>
         * Pseudo code for Set:
         * <code>
         * $instance.fieldName = value;
         * </code>
         * becomes:
         * <code>
         * $package/AndroidInstantRuntime.setPrivateField($instance, value, $fieldName);
         * </code>
         *
         * @param opcode      the field access opcode, can only be {@link Opcodes#PUTFIELD} or
         *                    {@link Opcodes#GETFIELD}
         * @param owner       the field declaring class
         * @param name        the field name
         * @param desc        the field type
         * @param accessRight the {@link AccessRight} for the field.
         * @return true if the field access was handled or false otherwise.
         */
        private boolean visitFieldAccess(
                int opcode, String owner, String name, String desc, AccessRight accessRight) {

            // if the accessed field is anything but public, we must go through reflection.
            boolean useReflection = accessRight != AccessRight.PUBLIC;

            // if the accessed field is accessed from within a constructor, it might be a public
            // final field that cannot be set by anything but the original constructor unless
            // we use reflection.
            if (!useReflection) {
                useReflection = isConstructor && (owner.equals(visitedClassName));
            }

            if (useReflection) {
                // we should make this more efficient, have a per field access type method
                // for getting and setting field values.
                switch (opcode) {
                    case Opcodes.GETFIELD:
                        // push declaring class
                        visitLdcInsn(Type.getType("L" + owner + ";"));

                        // the instance of the owner class we are getting the field value from
                        // is on top of the stack. It could be "this"
                        push(name);

                        // Stack :  <receiver>
                        //          <field_declaring_class>
                        //          <field_name>
                        invokeStatic(RUNTIME_TYPE,
                                Method.getMethod("Object getPrivateField(Object, Class, String)"));
                        // Stack : <field_value>
                        ByteCodeUtils.unbox(this, Type.getType(desc));
                        break;
                    case Opcodes.PUTFIELD:
                        // the instance of the owner class we are getting the field value from
                        // is second on the stack. It could be "this"
                        // top of the stack is the new value we are trying to set, box it.
                        box(Type.getType(desc));

                        // push declaring class
                        visitLdcInsn(Type.getType("L" + owner + ";"));
                        // push the field name.
                        push(name);
                        // Stack :  <receiver>
                        //          <boxed_field_value>
                        //          <field_declaring_class>
                        //          <field_name>
                        invokeStatic(RUNTIME_TYPE,
                                Method.getMethod(
                                        "void setPrivateField(Object, Object, Class, String)"));
                        break;
                    default:
                        throw new RuntimeException(
                                "VisitFieldAccess called with wrong opcode " + opcode);
                }
                return true;
            }
            // if this is a public field, no need to change anything we can access it from the
            // $override class.
            return false;
        }

        /**
         * Static field access visit.
         * So far we do not support class initializer "clinit" that would reset the static field
         * value in the class newer versions. Think about the case, where a static initializer
         * resets a static field value, we don't know if the current field value was set through
         * the initial class initializer or some code path, should we change the field value to the
         * new one ?
         * <p/>
         * For private static fields, the access instruction is rewritten to calls to reflection
         * to access the fields value:
         * <p/>
         * Pseudo code for Get:
         * <code>
         * value = $type.fieldName;
         * </code>
         * becomes:
         * <code>
         * value = (unbox)$package/AndroidInstantRuntime.getStaticPrivateField(
         * $type.class, $fieldName);
         * </code>
         * <p/>
         * Pseudo code for Set:
         * <code>
         * $type.fieldName = value;
         * </code>
         * becomes:
         * <code>
         * $package/AndroidInstantRuntime.setStaticPrivateField(value, $type.class $fieldName);
         * </code>
         *
         * @param opcode      the field access opcode, can only be {@link Opcodes#PUTSTATIC} or
         *                    {@link Opcodes#GETSTATIC}
         * @param name        the field name
         * @param desc        the field type
         * @param accessRight the {@link AccessRight} for the field.
         * @return true if the field access was handled or false
         */
        private boolean visitStaticFieldAccess(
                int opcode, String owner, String name, String desc, AccessRight accessRight) {

            if (accessRight != AccessRight.PUBLIC) {
                switch (opcode) {
                    case Opcodes.GETSTATIC:
                        // nothing of interest is on the stack.
                        visitLdcInsn(Type.getType("L" + owner + ";"));
                        push(name);
                        // Stack : <target_class>
                        //         <field_name>
                        invokeStatic(RUNTIME_TYPE,
                                Method.getMethod("Object getStaticPrivateField(Class, String)"));
                        // Stack : <field_value>
                        ByteCodeUtils.unbox(this, Type.getType(desc));
                        return true;
                    case Opcodes.PUTSTATIC:
                        // the new field value is on top of the stack.
                        // box it into an Object.
                        box(Type.getType(desc));
                        visitLdcInsn(Type.getType("L" + owner + ";"));
                        push(name);
                        // Stack :  <boxed_field_value>
                        //          <target_class>
                        //          <field_name>
                        invokeStatic(RUNTIME_TYPE,
                                Method.getMethod(
                                        "void setStaticPrivateField(Object, Class, String)"));
                        return true;
                    default:
                        throw new RuntimeException(
                                "VisitStaticFieldAccess called with wrong opcode " + opcode);
                }
            }
            return false;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                                    boolean itf) {

            boolean opcodeHandled = false;
            if (opcode == Opcodes.INVOKESPECIAL) {
                opcodeHandled = handleSpecialOpcode(owner, name, desc, itf);
            } else if (opcode == Opcodes.INVOKEVIRTUAL) {
                opcodeHandled = handleVirtualOpcode(owner, name, desc, itf);
            } else if (opcode == Opcodes.INVOKESTATIC) {
                opcodeHandled = handleStaticOpcode(owner, name, desc, itf);
            }
            if (!opcodeHandled) {
                mv.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }

        /**
         * Rewrites INVOKESPECIAL method calls:
         * <ul>
         * <li>calls to constructors are handled specially (see below)</li>
         * <li>calls to super methods are rewritten to call the 'access$super' trampoline we
         * injected into the original code</li>
         * <li>calls to methods in this class are rewritten to call the mathcin $override class
         * static method</li>
         * </ul>
         */
        private boolean handleSpecialOpcode(String owner, String name, String desc,
                                            boolean itf) {
            if (name.equals("<init>")) {
                return handleConstructor(owner, name, desc);
            }
            if (owner.equals(visitedClassName)) {
                String mtdSig = name + "." + desc;
                if (priNativeMtdSet.contains(mtdSig) || priSyncMtdSet.contains(mtdSig)) {
                    pushMethodRedirectArgumentsOnStack(name, desc);

                    // Stack : <receiver>
                    //      <array of parameter_values>
                    //      <array of parameter_types>
                    //      <method_name>
                    invokeStatic(RUNTIME_TYPE, Method.getMethod(
                            "Object invokeProtectedMethod(Object, Object[], Class[], String)"));
                    // Stack : <return value or null if no return value>
                    handleReturnType(desc);
                    return true;
                } else {
                    // private method dispatch, just invoke the $override class static method.
                    String newDesc = computeOverrideMethodDesc(desc, false /*isStatic*/);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, owner + "$override", name, newDesc, itf);
                    return true;
                }


            } else {
                String newDesc = computeOverrideMethodDesc(desc, false);
                InstantMethod method = new InstantMethod(owner, name, newDesc, desc);
                superMethods.add(method);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, visitedClassName + "$helper", name, newDesc, itf);
                return true;
            }
        }

        /**
         * Rewrites INVOKEVIRTUAL method calls.
         * <p/>
         * Virtual calls to protected methods are rewritten according to the following pseudo code:
         * before:
         * <code>
         * $value = $instance.protectedVirtual(arg1, arg2);
         * </code>
         * after:
         * <code>
         * $value = (unbox)$package/AndroidInstantRuntime.invokeProtectedMethod($instance,
         * new object[] {arg1, arg2}, new Class[] { String.class, Integer.class },
         * "protectedVirtual");
         * </code>
         */
        private boolean handleVirtualOpcode(String owner, String name, String desc, boolean itf) {

            AccessRight accessRight = getMethodAccessRight(owner, name, desc);
            if (accessRight == AccessRight.PUBLIC) {
                return false;
            }

            // for anything else, private, protected and package private, we must go through
            // reflection.
            // Stack : <receiver>
            //      <param_1>
            //      <param_2>
            //      ...
            //      <param_n>
            pushMethodRedirectArgumentsOnStack(name, desc);

            // Stack : <receiver>
            //      <array of parameter_values>
            //      <array of parameter_types>
            //      <method_name>
            invokeStatic(RUNTIME_TYPE, Method.getMethod(
                    "Object invokeProtectedMethod(Object, Object[], Class[], String)"));
            // Stack : <return value or null if no return value>
            handleReturnType(desc);
            return true;
        }

        /**
         * Rewrites INVOKESTATIC method calls.
         * <p/>
         * Static calls to non-public methods are rewritten according to the following pseudo code:
         * before:
         * <code>
         * $value = $type.protectedStatic(arg1, arg2);
         * </code>
         * after:
         * <code>
         * $value = (unbox)$package/AndroidInstantRuntime.invokeProtectedStaticMethod(
         * new object[] {arg1, arg2}, new Class[] { String.class, Integer.class },
         * "protectedStatic", $type.class);
         * </code>
         */
        private boolean handleStaticOpcode(String owner, String name, String desc, boolean itf) {
            AccessRight accessRight = getMethodAccessRight(owner, name, desc);
            if (accessRight == AccessRight.PUBLIC) {
                return false;
            }

            // for anything else, private, protected and package private, we must go through
            // reflection.

            // stack: <param_1>
            //      <param_2>
            //      ...
            //      <param_n>
            pushMethodRedirectArgumentsOnStack(name, desc);

            // push the class implementing the original static method
            visitLdcInsn(Type.getType("L" + owner + ";"));

            // stack: <boxed method parameter>
            //      <target parameter types>
            //      <target method name>
            //      <target class name>
            invokeStatic(RUNTIME_TYPE, Method.getMethod(
                    "Object invokeProtectedStaticMethod(Object[], Class[], String, Class)"));
            // stack : method return value or null if the method was VOID.
            handleReturnType(desc);
            return true;
        }


        /**
         * For calls to constructors in the same package, calls are rewritten to use reflection
         * to create the instance (see above, the NEW and DUP instructions are also removed) using
         * the following pseudo code.
         * <p/>
         * before:
         * <code>
         * $value = new $type(arg1, arg2);
         * </code>
         * after:
         * <code>
         * $value = ($type)$package/AndroidInstantRuntime.newForClass(new Object[] {arg1, arg2 },
         * new Class[]{ String.class, Integer.class }, $type.class);
         * </code>
         */
        private boolean handleConstructor(String owner, String name, String desc) {

            AccessRight accessRight = getMethodAccessRight(owner, name, desc);
            if (accessRight != AccessRight.PUBLIC) {
                Type expectedType = Type.getType("L" + owner + ";");
                pushMethodRedirectArgumentsOnStack(name, desc);

                // pop the name, we don't need it.
                pop();
                visitLdcInsn(expectedType);

                invokeStatic(RUNTIME_TYPE, Method.getMethod(
                        "Object newForClass(Object[], Class[], Class)"));

                checkCast(expectedType);
                ByteCodeUtils.unbox(this, expectedType);
                return true;
            } else {
                return false;
            }


        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start,
                                       Label end, int index) {
            // Even if we call the first argument of the static redirection "this", JDI has a
            // specific API to retrieve "thisObject" from the current stack frame, which totally
            // ignores and bypasses this variable declaration. We will not show the renamed
            // variable to the user and will redirect in Studio to be the real this object.
            // We use a name unlikely to be used, but different than "this".
            if ("this".equals(name)) {
                name = "$this";
            }
            super.visitLocalVariable(name, desc, signature, start, end, index);
        }

        @Override
        public void visitEnd() {

        }


        private void pushMethodRedirectArgumentsOnStack(String name, String desc) {
            Type[] parameterTypes = Type.getArgumentTypes(desc);

            // stack : <parameters values>
            int parameters = boxParametersToNewLocalArray(parameterTypes);
            // push the parameter values as a Object[] on the stack.
            loadLocal(parameters);

            // push the parameter types as a Class[] on the stack
            pushParameterTypesOnStack(parameterTypes);

            push(name);
        }

        /**
         * Creates an array of {@link Class} objects with the same size of the array of the passed
         * parameter types. For each parameter type, stores its {@link Class} object into the
         * result array. For intrinsic types which are not present in the class constant pool, just
         * push the actual {@link Type} object on the stack and let ASM do the rest. For non
         * intrinsic type use a {@link MethodVisitor#visitLdcInsn(Object)} to ensure the
         * referenced class's presence in this class constant pool.
         * <p/>
         * Stack Before : nothing of interest
         * Stack After : <array of {@link Class}>
         *
         * @param parameterTypes a method list of parameters.
         */
        private void pushParameterTypesOnStack(Type[] parameterTypes) {
            push(parameterTypes.length);
            newArray(Type.getType(Class.class));

            for (int i = 0; i < parameterTypes.length; i++) {
                dup();
                push(i);
                switch (parameterTypes[i].getSort()) {
                    case Type.OBJECT:
                    case Type.ARRAY:
                        visitLdcInsn(parameterTypes[i]);
                        break;
                    case Type.BOOLEAN:
                    case Type.CHAR:
                    case Type.BYTE:
                    case Type.SHORT:
                    case Type.INT:
                    case Type.LONG:
                    case Type.FLOAT:
                    case Type.DOUBLE:
                        push(parameterTypes[i]);
                        break;
                    default:
                        throw new RuntimeException(
                                "Unexpected parameter type " + parameterTypes[i]);

                }
                arrayStore(Type.getType(Class.class));
            }
        }

        /**
         * Handle method return logic.
         *
         * @param desc the method signature
         */
        private void handleReturnType(String desc) {
            Type ret = Type.getReturnType(desc);
            if (ret.getSort() == Type.VOID) {
                pop();
            } else {
                ByteCodeUtils.unbox(this, ret);
            }
        }

        private int boxParametersToNewLocalArray(Type[] parameterTypes) {
            int parameters = newLocal(Type.getType("[Ljava/lang.Object;"));
            push(parameterTypes.length);
            newArray(Type.getType(Object.class));
            storeLocal(parameters);

            for (int i = parameterTypes.length - 1; i >= 0; i--) {
                loadLocal(parameters);
                swap(parameterTypes[i], Type.getType(Object.class));
                push(i);
                swap(parameterTypes[i], Type.INT_TYPE);
                box(parameterTypes[i]);
                arrayStore(Type.getType(Object.class));
            }
            return parameters;
        }
    }

    @Override
    public void visitEnd() {
        addDispatchMethod();
        addSupportMethod();
        super.visitEnd();
    }

    /**
     * To each class, add the dispatch method called by the original code that acts as a trampoline to
     * invoke the changed methods.
     * <p/>
     * Pseudo code:
     * <code>
     * Object access$dispatch(String name, object[] args) {
     * if (name.equals(
     * "firstMethod.(L$type;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;")) {
     * return firstMethod(($type)arg[0], (String)arg[1], arg[2]);
     * }
     * if (name.equals("secondMethod.(L$type;Ljava/lang/String;I;)V")) {
     * secondMethod(($type)arg[0], (String)arg[1], (int)arg[2]);
     * return;
     * }
     * ...
     * StringBuilder $local1 = new StringBuilder();
     * $local1.append("Method not found ");
     * $local1.append(name);
     * $local1.append(" in " + visitedClassName +
     * "$dispatch implementation, restart the application");
     * throw new $package/InstantReloadException($local1.toString());
     * }
     * </code>
     */
    private void addDispatchMethod() {
        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_VARARGS;
        Method m = new Method("access$dispatch", "(I[Ljava/lang/Object;)Ljava/lang/Object;");
        MethodVisitor visitor = super.visitMethod(access,
                m.getName(),
                m.getDescriptor(),
                null, null);

        final GeneratorAdapter mv = new GeneratorAdapter(access, m, visitor);

        if (TRACING_ENABLED) {
            mv.push("Redirecting ");
            mv.loadArg(0);
            trace(mv, 2);
        }

        List<MethodNode> allMethods = new ArrayList();

        // if we are disabled, do not generate any dispatch, the method will throw an exception
        // if invoked which should never happen.
        if (!instantRunDisabled) {
            //noinspection unchecked
            allMethods.addAll(classNode.methods);
            allMethods.addAll(addedMethods);
        }

        final Map<String, MethodNode> methods = new HashMap();
        for (MethodNode methodNode : allMethods) {
            if (methodNode.name.equals("<clinit>") || methodNode.name.equals("<init>")) {
                continue;
            }
            if (!isAccessCompatibleWithInstantRun(methodNode.access)) {
                continue;
            }
            methods.put(methodNode.name + "." + methodNode.desc, methodNode);
        }

        new IntSwitch() {
            @Override
            void visitString() {
                mv.visitVarInsn(Opcodes.ALOAD, 1);
            }

            @Override
            void visitInt() {
                mv.visitVarInsn(Opcodes.ILOAD, 1);
            }

            @Override
            void visitCase(String methodName) {
                MethodNode methodNode = methods.get(methodName);
                String name = methodNode.name;
                boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) != 0;
                String newDesc =
                        computeOverrideMethodDesc(methodNode.desc, isStatic);

                if (TRACING_ENABLED) {
                    trace(mv, "M: " + name + " P:" + newDesc);
                }
                Type[] args = Type.getArgumentTypes(newDesc);
                int argc = 0;
                for (Type t : args) {
                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                    mv.push(argc);
                    mv.visitInsn(Opcodes.AALOAD);
                    ByteCodeUtils.unbox(mv, t);
                    argc++;
                }
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, visitedClassName + "$override",
                        isStatic ? computeOverrideMethodName(name, methodNode.desc) : name,
                        newDesc, false);
                Type ret = Type.getReturnType(methodNode.desc);
                if (ret.getSort() == Type.VOID) {
                    mv.visitInsn(Opcodes.ACONST_NULL);
                } else {
                    mv.box(ret);
                }
                mv.visitInsn(Opcodes.ARETURN);
            }

            @Override
            void visitDefault() {
                writeMissingMessageWithHash(mv, visitedClassName);
            }
        }.visit(mv, methods.keySet(), visitedClassName);

        mv.visitMaxs(0, 0);
        mv.visitEnd();


    }

    public void addSupportMethod() {
        int access = Opcodes.ACC_PUBLIC;
        Method m = new Method("isSupport", "(I)Z");
        MethodVisitor mv = super.visitMethod(access,
                m.getName(),
                m.getDescriptor(),
                null, null);

        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
//        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false);

        int[] hashArray = new int[fixMtds.size()];
        Label[] labelArray = new Label[fixMtds.size()];
        Label l0 = new Label();
        Label l1 = new Label();
        for (int i = 0; i < fixMtds.size(); i++) {
            hashArray[i] = AcesoProguardMap.instance().getClassData(visitedClassName).getMtdIndex(fixMtds.get(i));
            labelArray[i] = l0;
        }

        mv.visitLookupSwitchInsn(l1, hashArray, labelArray);
        mv.visitLabel(l0);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(l1);
        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 2);
        mv.visitEnd();

        mv.visitMaxs(0, 0);
        mv.visitEnd();

    }


    /**
     * Returns true if the passed class name is in the same package as the visited class.
     *
     * @param type The type name of the other object, either a "com/var/Object" or a "[Type" one.
     * @return true if className and visited class are in the same java package.
     */
    private boolean isInSamePackage(String type) {

        if (type.charAt(0) == '[') {
            return false;
        }
        return getPackage(visitedClassName).equals(getPackage(type));
    }

    /**
     * @return the package of the given / separated class name.
     */
    private String getPackage(String className) {
        int i = className.lastIndexOf('/');
        return i == -1 ? className : className.substring(0, i);
    }

    /**
     * Returns true if the passed class name is an ancestor of the visited class.
     *
     * @param className a / separated class name
     * @return true if it is an ancestor, false otherwise.
     */
    private boolean isAnAncestor(String className) {
        for (ClassNode parentNode : parentNodes) {
            if (parentNode.name.equals(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Instance methods, when converted to static methods need to have the subject object as
     * the first parameter. If the method is static, it is unchanged.
     */

    private String computeOverrideMethodDesc(String desc, boolean isStatic) {
        if (isStatic) {
            return desc;
        } else {
            return instanceToStaticDescPrefix + desc.substring(1);
        }
    }

    /**
     * Prevent method name collisions.
     * <p/>
     * A static method that takes an instance of this class as the first argument might clash with
     * a rewritten instance method, and this rewrites all methods like that. This is an
     * over-approximation of the necessary renames, but it has the advantage of neither adding
     * additional state nor requiring lookups.
     */

    private String computeOverrideMethodName(String name, String desc) {
        if (desc.startsWith(instanceToStaticDescPrefix)
                && !name.equals("init$args")
                && !name.equals("init$body")) {
            return METHOD_MANGLE_PREFIX + name;
        }
        return name;
    }
}
