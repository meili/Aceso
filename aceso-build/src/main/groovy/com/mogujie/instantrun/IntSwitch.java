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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import org.gradle.api.GradleException;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;


abstract class IntSwitch {

    AcesoProguardMap.ClassData classData = null;
    String className = null;

    final Function<String, Integer> HASH_METHOD = new Function<String, Integer>() {
        @Override
        public Integer apply(String input) {
            Integer mtdIndex = classData.getMtdIndex(input);
            if (mtdIndex == null) {
                throw new RuntimeException("the method : " + input + " in class : " + className
                        + " not found in the aceso-mapping. \n "
                        + "sure you aceso-mapping is right and no new method.");
            }
            return mtdIndex;
        }
    };

    private static final Type OBJECT_TYPE = Type.getType(Object.class);
    private static final Type STRING_TYPE = Type.getType(String.class);
    private static final Type INSTANT_RELOAD_EXCEPTION_TYPE =
            Type.getObjectType(IncrementalVisitor.PACKAGE + "/InstantReloadException");

    abstract void visitString();

    abstract void visitInt();

    abstract void visitCase(String string);

    abstract void visitDefault();

    private void visitx(GeneratorAdapter mv, List<String> strings) {
        if (strings.size() == 1) {
            visitCase(strings.get(0));
            return;
        }
        for (String string : strings) {
            Label label = new Label();
            visitString();
            mv.visitLdcInsn(string);
            mv.invokeVirtual(STRING_TYPE, Method.getMethod("boolean equals(Object)"));
            mv.visitJumpInsn(Opcodes.IFEQ, label);
            visitCase(string);
            mv.visitLabel(label);
        }

        visitDefault();
    }

    private void visitClassifier(GeneratorAdapter mv, Set<String> strings) {
        visitInt();

        Multimap<Integer, String> buckets = Multimaps.index(strings, HASH_METHOD);
        List<Map.Entry<Integer, Collection<String>>> sorted = Ordering.natural()
                .onResultOf(new Function<Map.Entry<Integer, Collection<String>>, Integer>() {
                    @Override
                    public Integer apply(Map.Entry<Integer, Collection<String>> entry) {
                        return entry.getKey();
                    }
                }).immutableSortedCopy(buckets.asMap().entrySet());

        int sortedHashes[] = new int[sorted.size()];
        List<String> sortedCases[] = new List[sorted.size()];
        int index = 0;
        for (Map.Entry<Integer, Collection<String>> entry : sorted) {
            sortedHashes[index] = entry.getKey();
            sortedCases[index] = Lists.newCopyOnWriteArrayList(entry.getValue());
            index++;
        }

        // Label for each hash and for default.
        Label labels[] = new Label[sorted.size()];
        Label defaultLabel = new Label();
        for (int i = 0; i < sorted.size(); ++i) {
            labels[i] = new Label();
        }

        // Create a switch that dispatches to each label from the hash code of
        mv.visitLookupSwitchInsn(defaultLabel, sortedHashes, labels);

        // Create the cases.
        for (int i = 0; i < sorted.size(); ++i) {
            mv.visitLabel(labels[i]);
            visitx(mv, sortedCases[i]);
        }
        mv.visitLabel(defaultLabel);
        visitDefault();
    }

    void writeMissingMessageWithHash(GeneratorAdapter mv, String visitedClassName) {
        mv.newInstance(INSTANT_RELOAD_EXCEPTION_TYPE);
        mv.dup();
        mv.push("int switch could not find %d in %s");
        mv.push(3);
        mv.newArray(OBJECT_TYPE);
        mv.dup();
        mv.push(0);
        visitInt();
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/Integer",
                "valueOf",
                "(I)Ljava/lang/Integer;", false);
        mv.arrayStore(OBJECT_TYPE);
        mv.dup();
        mv.push(2);
        mv.push(visitedClassName);
        mv.arrayStore(OBJECT_TYPE);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/String",
                "format",
                "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false);
        mv.invokeConstructor(INSTANT_RELOAD_EXCEPTION_TYPE,
                Method.getMethod("void <init> (String)"));
        mv.throwException();
    }

    void visit(GeneratorAdapter mv, Set<String> strings, String className) {
        this.className = className;
        this.classData = AcesoProguardMap.instance().getClassData(className);
        if (classData == null) {
            throw new GradleException("can not find class: " + className +
                    " , sure you aceso-mapping is right and this class not in blacklist.");
        }
        visitClassifier(mv, strings);
    }
}
