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

import org.objectweb.asm.commons.Method;

/**
 * The class hold oriDesc and owner.
 *
 * @author wangzhi
 */
public class InstantMethod extends Method {
    private String oriDesc;
    private String owner;

    public InstantMethod(String owner, String name, String desc, String oriDesc) {
        super(name, desc);
        this.oriDesc = oriDesc;
        this.owner = owner;
    }

    public String getOriDesc() {
        return oriDesc;
    }


    public String getOwner() {
        return owner;
    }
}
