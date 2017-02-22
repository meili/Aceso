/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.collect.Lists;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.List;

/**
 * Bytecode generation utilities to work around some ASM / Dex issues.
 */
public class ByteCodeUtils {

    public static final String CONSTRUCTOR = "<init>";
    public static final String CLASS_INITIALIZER = "<clinit>";
    private static final Type NUMBER_TYPE = Type.getObjectType("java/lang/Number");
    private static final Method SHORT_VALUE = Method.getMethod("short shortValue()");
    private static final Method BYTE_VALUE = Method.getMethod("byte byteValue()");

    /**
     * Generates unboxing bytecode for the passed type. An {@link Object} is expected to be on the
     * stack when these bytecodes are inserted.
     *
     * ASM takes a short cut when dealing with short/byte types and convert them into int rather
     * than short/byte types. This is not an issue on the jvm nor Android's ART but it is an issue
     * on Dalvik.
     *
     * @param mv the {@link GeneratorAdapter} generating a method implementation.
     * @param type the expected un-boxed type.
     */
    public static void unbox(GeneratorAdapter mv, Type type) {
        if (type.equals(Type.SHORT_TYPE)) {
            mv.checkCast(NUMBER_TYPE);
            mv.invokeVirtual(NUMBER_TYPE, SHORT_VALUE);
        } else if (type.equals(Type.BYTE_TYPE)) {
            mv.checkCast(NUMBER_TYPE);
            mv.invokeVirtual(NUMBER_TYPE, BYTE_VALUE);
        } else {
            mv.unbox(type);
        }
    }

    /**
     * Pushes an array on the stack that contains the value of all the given variables.
     */
    static void newVariableArray(
             GeneratorAdapter mv,
             List<LocalVariable> variables) {
        mv.push(variables.size());
        mv.newArray(Type.getType(Object.class));
        loadVariableArray(mv, variables, 0);
    }

    /**
     * Given an array on the stack, it loads it with the values of the given variables stating at
     * offset.
     */
    static void loadVariableArray(
             GeneratorAdapter mv,
             List<LocalVariable> variables, int offset) {
        // we need to maintain the stack index when loading parameters from, as for long and double
        // values, it uses 2 stack elements, all others use only 1 stack element.
        for (int i = offset; i < variables.size(); i++) {
            LocalVariable variable = variables.get(i);
            // duplicate the array of objects reference, it will be used to store the value in.
            mv.dup();
            // index in the array of objects to store the boxed parameter.
            mv.push(i);
            // Pushes the appropriate local variable on the stack
            mv.visitVarInsn(variable.type.getOpcode(Opcodes.ILOAD), variable.var);
            // potentially box up intrinsic types.
            mv.box(variable.type);
            // store it in the array
            mv.arrayStore(Type.getType(Object.class));
        }
    }


    /**
     * Converts Types to LocalVariables, assuming they start from variable 0.
     */
    static List<LocalVariable> toLocalVariables( List<Type> types) {
        List<LocalVariable> variables = Lists.newArrayList();
        int stack = 0;
        for (int i = 0; i < types.size(); i++) {
            Type type = types.get(i);
            variables.add(new LocalVariable(type, stack));
            stack += type.getSize();
        }
        return variables;
    }

}
