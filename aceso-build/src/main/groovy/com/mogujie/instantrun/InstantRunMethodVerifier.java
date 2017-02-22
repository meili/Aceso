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

import com.google.common.collect.ImmutableMultimap;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.MethodNode;

/**
 * Verifies that a method implementation is compatible.
 */
public class InstantRunMethodVerifier {

    /**
     * Verifies a method implementation against the blacklisted list of APIs.
     */
    public static boolean verifyMethod(MethodNode method) {
        VerifierMethodVisitor mv = new VerifierMethodVisitor(method);
        method.accept(mv);
        return (mv.incompatibleChange == InstantRunVerifierStatus.INCOMPATIBLE);
    }

    public static class VerifierMethodVisitor extends MethodNode {
        InstantRunVerifierStatus incompatibleChange = InstantRunVerifierStatus.COMPATIBLE;
        public VerifierMethodVisitor(MethodNode method) {
            super(Opcodes.ASM5, method.access, method.name, method.desc, method.signature,
                    (String[]) method.exceptions.toArray(new String[method.exceptions.size()]));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                                    boolean itf) {
            Type receiver = Type.getObjectType(owner);
            if (incompatibleChange != InstantRunVerifierStatus.INCOMPATIBLE) {
                if (opcode == Opcodes.INVOKEVIRTUAL && blackListedMethods.containsKey(receiver)) {
                    for (Method method : blackListedMethods.get(receiver)) {
                        if (method.getName().equals(name) && method.getDescriptor().equals(desc)) {
                            incompatibleChange = InstantRunVerifierStatus.INCOMPATIBLE;
                        }
                    }
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }

    /**
     * If a method is called methods in the blacklist,
     * we will not instrument it.
     */
    private static final ImmutableMultimap<Type, Method> blackListedMethods =
            ImmutableMultimap.<Type, Method>builder().build();
}
