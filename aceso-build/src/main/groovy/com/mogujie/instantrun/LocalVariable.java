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

import org.objectweb.asm.Type;

/**
 * Represents a local variable within a method.
 */
class LocalVariable {

    public final Type type;

    public final int var;

    LocalVariable( Type type, int var) {
        this.type = type;
        this.var = var;
    }

    @Override
    public boolean equals(Object obj) {
        // Equality is seen in a per method context so there is no need to check the type.
        return obj instanceof LocalVariable && ((LocalVariable) obj).var == var;
    }

    @Override
    public int hashCode() {
        return var;
    }
}
