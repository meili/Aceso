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

import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;


public class IncrementalSupportVisitor extends IncrementalVisitor {

    private boolean disableRedirectionForClass = false;

    private static final class VisitorBuilder implements IncrementalVisitor.VisitorBuilder {

        private VisitorBuilder() {
        }


        @Override
        public IncrementalVisitor build(
                 ClassNode classNode,
                 List<ClassNode> parentNodes,
                 ClassVisitor classVisitor) {
            return new IncrementalSupportVisitor(classNode, parentNodes, classVisitor);
        }

        @Override

        public String getMangledRelativeClassFilePath( String originalClassFilePath) {
            return originalClassFilePath;
        }


        @Override
        public OutputType getOutputType() {
            return OutputType.INSTRUMENT;
        }
    }


    public static final IncrementalVisitor.VisitorBuilder VISITOR_BUILDER = new VisitorBuilder();


    public IncrementalSupportVisitor(
             ClassNode classNode,
             List<ClassNode> parentNodes,
             ClassVisitor classVisitor) {
        super(classNode, parentNodes, classVisitor);
    }

    
    @Override
    public void visit(int version, int access, String name, String signature, String superName,
                      String[] interfaces) {
        visitedClassName = name;
        visitedSuperName = superName;

        AcesoProguardMap.instance().putClass(visitedClassName);
        access = IncrementalTool.transformClassAccessForInstantRun(access);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        int newAccess =
                access & (~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
        super.visitInnerClass(name, outerName, innerName, newAccess);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (desc.equals(DISABLE_ANNOTATION_TYPE.getDescriptor())) {
            disableRedirectionForClass = true;
        }
        return super.visitAnnotation(desc, visible);
    }


    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
                                   Object value) {
//        InstantProguardMap.instance().putField(InstantRunTool.getFieldSig(name, desc));
//        access = transformAccessForInstantRun(access);
        //make all field to public
        access &= ~Opcodes.ACC_PROTECTED;
        access &= ~Opcodes.ACC_PRIVATE;
        access = access | Opcodes.ACC_PUBLIC;
        return super.visitField(access, name, desc, signature, value);
    }


    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                     String[] exceptions) {
        AcesoProguardMap.instance().putMethod(visitedClassName, IncrementalTool.getMtdSig(name, desc));
        access = IncrementalTool.transformAccessForInstantRun(access);

        MethodVisitor defaultVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        MethodNode method = getMethodByNameInClass(name, desc, classNode);
        // does the method use blacklisted APIs.
        boolean hasIncompatibleChange = InstantRunMethodVerifier.verifyMethod(method);

        if (hasIncompatibleChange || disableRedirectionForClass
                || !isAccessCompatibleWithInstantRun(access)
                || name.equals(ByteCodeUtils.CLASS_INITIALIZER)) {
            return defaultVisitor;
        } else {
            ArrayList<Type> args = new ArrayList<Type>(Arrays.asList(Type.getArgumentTypes(desc)));
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            if (!isStatic) {
                args.add(0, Type.getType(Object.class));
            }

            ISMethodVisitor mv = new ISMethodVisitor(defaultVisitor, access, name, desc);
            if (name.equals(ByteCodeUtils.CONSTRUCTOR)) {

            } else {
                mv.addRedirection(new MethodRedirection(
                        new LabelNode(mv.getStartLabel()),
                        visitedClassName,
                        name,
                        desc,
                        args,
                        Type.getReturnType(desc), isStatic));
            }
            method.accept(mv);
            return null;
        }
    }


    private class ISMethodVisitor extends GeneratorAdapter {

        private boolean disableRedirection = false;
        private int change;
        private final List<Type> args;
        private final List<Redirection> redirections;
        private final Map<Label, Redirection> resolvedRedirections;
        private final Label start;
        private String name;
        private String desc;

        public ISMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM5, mv, access, name, desc);
            this.name = name;
            this.desc = desc;
            this.change = -1;
            this.redirections = new ArrayList();
            this.resolvedRedirections = new HashMap();
            this.args = new ArrayList(Arrays.asList(Type.getArgumentTypes(desc)));
            this.start = new Label();
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            // if this is not a static, we add a fictional first parameter what will contain the
            // "this" reference which can be loaded with ILOAD_0 bytecode.
            if (!isStatic) {
                args.add(0, Type.getType(Object.class));
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (desc.equals(DISABLE_ANNOTATION_TYPE.getDescriptor())) {
                disableRedirection = true;
            }
            return super.visitAnnotation(desc, visible);
        }

        
        @Override
        public void visitCode() {
            if (!disableRedirection) {
                // Labels cannot be used directly as they are volatile between different visits,
                // so we must use LabelNode and resolve before visiting for better performance.
                for (Redirection redirection : redirections) {
                    resolvedRedirections.put(redirection.getPosition().getLabel(), redirection);
                }

                super.visitLabel(start);
                change = newLocal(MTD_MAP_TYPE);

                push(new Integer(AcesoProguardMap.instance().getClassIndex(visitedClassName)));
                push(new Integer(AcesoProguardMap.instance().getMtdIndex(visitedClassName, IncrementalTool.getMtdSig(name, desc))));
                invokeStatic(IncrementalVisitor.MTD_MAP_TYPE, Method.getMethod("com.android.tools.fd.runtime.IncrementalChange get(int,int)"));

                storeLocal(change);

                redirectAt(start);
            }
            super.visitCode();
        }

        @Override
        public void visitLabel(Label label) {
            super.visitLabel(label);
            redirectAt(label);
        }

        private void redirectAt(Label label) {
            if (disableRedirection) return;
            Redirection redirection = resolvedRedirections.get(label);
            if (redirection != null) {
                // A special line number to mark this area of code.
                super.visitLineNumber(0, label);
                redirection.redirect(this, change);
            }
        }

        public void addRedirection( Redirection redirection) {
            redirections.add(redirection);
        }


        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start,
                                       Label end, int index) {
            // In dex format, the argument names are separated from the local variable names. It
            // seems to be needed to declare the local argument variables from the beginning of
            // the methods for dex to pick that up. By inserting code before the first label we
            // break that. In Java this is fine, and the debugger shows the right thing. However
            // if we don't readjust the local variables, we just don't see the arguments.
            if (!disableRedirection && index < args.size()) {
                start = this.start;
            }
            super.visitLocalVariable(name, desc, signature, start, end, index);
        }

        public Label getStartLabel() {
            return start;
        }
    }

}
