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

package com.mogujie.aceso.processor

import com.android.build.gradle.internal.pipeline.TransformTask
import com.mogujie.aceso.Extension
import com.mogujie.aceso.HookWrapper
import com.mogujie.aceso.util.GradleUtil
import com.mogujie.aceso.util.Log
import com.mogujie.aceso.util.ProguardUtil
import org.gradle.api.Project

import static com.mogujie.aceso.Constant.*

/**
 * The class processor for expand class scope.
 *
 * @author wangzhi
 */
public class ExpandScopeProcessor extends ClassProcessor {
    ExpandScopeProcessor(Project project, def variant, Extension config) {
        super(project, variant, config)
    }

    @Override
    File getMergedJar() {
        return getFileInAceso(MERGED_DIR, varDirName, MERGED_EXPAND_JAR)
    }

    @Override
    File getOutJar() {
        return getFileInAceso(EXPAND_DIR, varDirName, EXPAND_JAR)
    }

    @Override
    void process() {
        Log.i("expand class in hotfix project..")
        TransformTask proguardTask = GradleUtil.getProguardTask(project, varName)
        if (proguardTask != null) {
            ProguardUtil.instance().initProguardMap(proguardTask.transform.getMappingFile())
        }
        HookWrapper.expandScope(getMergedJar(), getOutJar())
    }

}
