/*
 *
 *  * Copyright (C) 2017 meili-inc company
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.mogujie.instantrun;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.List;

/**
 * A ClassWriter used for generating the $helper class.
 *
 * @author wangzhi
 */
public class SuperHelperVisitor extends ClassWriter implements Opcodes {
    IncrementalChangeVisitor visitor;
    ClassNode superNode;

    public SuperHelperVisitor(int api, IncrementalChangeVisitor visitor, ClassNode superNode) {
        super(api);
        this.visitor = visitor;
        this.superNode = superNode;
    }

    public void start() {
        visit(Opcodes.V1_7, ACC_PUBLIC + ACC_SUPER, visitor.visitedClassName + "$helper", null, visitor.visitedSuperName, null);
        for (int nodeIndex = 0; nodeIndex < superNode.methods.size(); nodeIndex++) {
            MethodNode methodNode = (MethodNode) superNode.methods.get(nodeIndex);
            if ("<init>".equals(methodNode.name)) {
                String[] exceptions = null;
                if (methodNode.exceptions != null) {
                    exceptions= (String[]) methodNode.exceptions.toArray(new String[0]);
                }
                MethodVisitor mv = visitMethod(ACC_PUBLIC, methodNode.name, methodNode.desc, methodNode.signature, exceptions);
                mv.visitCode();
                Type[] args = Type.getArgumentTypes(methodNode.desc);
                List<LocalVariable> variables = ByteCodeUtils.toLocalVariables(Arrays.asList(args));
                mv.visitVarInsn(ALOAD, 0);
                int local = 1;
                for (int i = 0; i < variables.size(); i++) {
                    mv.visitVarInsn(variables.get(i).type.getOpcode(Opcodes.ILOAD), variables.get(i).var + 1);
                    local = variables.get(i).var + 1 + variables.get(i).type.getSize();
                }
                mv.visitMethodInsn(INVOKESPECIAL, superNode.name, methodNode.name, methodNode.desc, false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(local, local);
                mv.visitEnd();
            }
        }

        for (InstantMethod method : visitor.superMethods) {
            MethodVisitor mv = visitMethod(ACC_PUBLIC + ACC_STATIC, method.getName(), method.getDescriptor(), null, null);
            mv.visitCode();
            Type[] args = Type.getArgumentTypes(method.getDescriptor());

            List<LocalVariable> variables = ByteCodeUtils.toLocalVariables(Arrays.asList(args));
            int totSize = 1;
            for (LocalVariable variable : variables) {

                mv.visitVarInsn(variable.type.getOpcode(Opcodes.ILOAD), variable.var);
                totSize = variable.var;
            }

            mv.visitMethodInsn(INVOKESPECIAL, method.getOwner(), method.getName(), method.getOriDesc(), false);


            Type returnType = Type.getReturnType(method.getDescriptor());
            mv.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            mv.visitMaxs(totSize + 1, totSize + 1);
            mv.visitEnd();
        }

        visitEnd();
    }

}
