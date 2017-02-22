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

package com.mogujie.aceso

import com.mogujie.aceso.processor.HostClassProcessor
import com.mogujie.aceso.processor.ProguardProcessor
import com.mogujie.aceso.transoform.HookDexTransform
import com.mogujie.aceso.transoform.HookProguardTransform
import com.mogujie.aceso.transoform.HookTransform

/**
 * A plugin for instrument the host project.
 *
 * @author wangzhi
 */

public class AcesoHostPlugin extends AcesoBasePlugin {

    @Override
    protected void realApply() {
        project.android.applicationVariants.all { variant ->
            doIt(variant)
        }
    }

    public void doIt(def variant) {
        String varName = variant.name.capitalize()
        String varDirName = variant.getDirName()
        if (!config.instrumentDebug && varName.toLowerCase().contains("debug")) {
            return;
        }
        addProguardKeepRule(variant)
        //inject proguard tramsform
        HookTransform.injectTransform(project, variant,
                new ProguardProcessor(project, variant, config),
                HookProguardTransform.BUILDER)
        //inject dex tramsform
        HookTransform.injectTransform(project, variant,
                new HostClassProcessor(project, variant, config),
                HookDexTransform.BUILDER)
    }

}
