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

import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A tool for byte code processing.
 *
 * @author wangzhi
 */
public class IncrementalTool {

    public static byte[] getPatchFileContents(
             ImmutableList<String> patchFileContents,  ImmutableList<Integer> patchIndexContents) {
        if (patchFileContents.size() != patchIndexContents.size()) {
            throw new GradleException("patchFileContents's size is "
                    + patchFileContents.size() + ", but patchIndexContents's size is "
                    + patchIndexContents.size() + ", please check the changed classes.");
        }
        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                IncrementalVisitor.APP_PATCHES_LOADER_IMPL, null,
                IncrementalVisitor.ABSTRACT_PATCHES_LOADER_IMPL, null);

        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    IncrementalVisitor.ABSTRACT_PATCHES_LOADER_IMPL,
                    "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC,
                    "getPatchedClasses", "()[Ljava/lang/String;", null, null);
            mv.visitCode();

            mv.visitIntInsn(Opcodes.SIPUSH, patchFileContents.size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
            for (int index = 0; index < patchFileContents.size(); index++) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitIntInsn(Opcodes.SIPUSH, index);
                mv.visitLdcInsn(patchFileContents.get(index));
                mv.visitInsn(Opcodes.AASTORE);
            }
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(4, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC,
                    "getPatchedClassIndexes", "()[I", null, null);
            mv.visitCode();

            mv.visitIntInsn(Opcodes.SIPUSH, patchIndexContents.size());
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
            for (int index = 0; index < patchIndexContents.size(); index++) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitIntInsn(Opcodes.SIPUSH, index);
                mv.visitLdcInsn(patchIndexContents.get(index));
                mv.visitInsn(Opcodes.IASTORE);
            }
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(4, 1);
            mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();

    }

    public static String getMtdSig(String mtdName, String mtdDesc) {
        return mtdName + "." + mtdDesc;
    }

    public static String getFieldSig(String filedName, String fieldDesc) {
        return fieldDesc + "." + filedName;
    }

    public static int transformAccessToPublic(int access) {
        access &= ~Opcodes.ACC_PROTECTED;
        access &= ~Opcodes.ACC_PRIVATE;
        return access | Opcodes.ACC_PUBLIC;
    }

    public static int transformAccessForInstantRun(int access) {
        IncrementalVisitor.AccessRight accessRight = IncrementalVisitor.AccessRight.fromNodeAccess(access);
        if (accessRight != IncrementalVisitor.AccessRight.PRIVATE) {
            access &= ~Opcodes.ACC_PROTECTED;
            access &= ~Opcodes.ACC_PRIVATE;
            return access | Opcodes.ACC_PUBLIC;
        }
        return access;
    }

    public static int transformClassAccessForInstantRun(int access) {
        IncrementalVisitor.AccessRight accessRight = IncrementalVisitor.AccessRight.fromNodeAccess(access);
        int fixedVisibility = accessRight == IncrementalVisitor.AccessRight.PACKAGE_PRIVATE
                ? access | Opcodes.ACC_PUBLIC
                : access;

        // TODO: only do this on KitKat?
        return fixedVisibility | Opcodes.ACC_SUPER;
    }

    private static boolean sMethodLevelFix = true;

    public static void setMethodLevelFix(boolean methodLevelFix) {
        sMethodLevelFix = methodLevelFix;
    }

    public static boolean isMethodLevelFix() {
        return sMethodLevelFix;
    }

}
