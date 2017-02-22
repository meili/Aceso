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

import com.mogujie.aceso.Extension
import com.mogujie.aceso.util.FileUtils
import com.mogujie.aceso.util.GradleUtil
import org.gradle.api.Project

/**
 * The class processor.
 *
 * @author wangzhi
 */

public abstract class ClassProcessor {

    Project project

    String varName

    String varDirName

    Extension config

    ClassProcessor(Project project, def variant, Extension config) {
        this.project = project
        this.varName = variant.name.capitalize()
        this.varDirName = variant.getDirName()
        this.config = config
    }


    /**
     * Return the jar file which includes all classes.
     */
    abstract File getMergedJar()

    /**
     * Return the jar file which includes
     * all classes that have been processed.
     */
    abstract File getOutJar()

    /**
     * processing the class file.
     */
    abstract void process()

    /**
     * init env
     */
    void prepare() {
        File mergedJar = getMergedJar()
        if (mergedJar != null) {
            FileUtils.initFile(mergedJar)
        }
        File outJar = getOutJar()
        if (outJar != null) {
            FileUtils.initFile(outJar)
        }
    }

    protected File getFileInAceso(String category, String varDirName, String fileName) {
        GradleUtil.getFileInAceso(project, category, varDirName, fileName)
    }

}
