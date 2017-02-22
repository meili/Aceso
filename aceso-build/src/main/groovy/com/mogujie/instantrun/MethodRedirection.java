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

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.LabelNode;

import java.util.List;

public class MethodRedirection extends Redirection {

    MethodRedirection( LabelNode label, String visitedClassName, String mtdName, String mtdDesc,  List<Type> types,  Type type, boolean isStatic) {
        super(label, visitedClassName, mtdName, mtdDesc, types, type, isStatic);
    }


    @Override
    protected void doRedirect( GeneratorAdapter mv, int change) {
        mv.loadLocal(change);
        mv.push(AcesoProguardMap.instance().getMtdIndex(visitedClassName, IncrementalTool.getMtdSig(mtdName,mtdDesc)));
        ByteCodeUtils.newVariableArray(mv, ByteCodeUtils.toLocalVariables(types));

        // now invoke the generic dispatch method.
        mv.invokeInterface(IncrementalVisitor.CHANGE_TYPE, Method.getMethod("Object access$dispatch(int, Object[])"));
    }
}
