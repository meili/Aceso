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
import org.gradle.api.Project

import static com.mogujie.aceso.Constant.ALL_CLASSES_DIR
import static com.mogujie.aceso.Constant.ALL_CLASSES_JAR

/**
 * Created by wangzhi on 17/2/8.
 */
public class ProguardProcessor extends ClassProcessor {

    ProguardProcessor(Project project, def variant, Extension config) {
        super(project, variant, config)
    }

    @Override
    File getMergedJar() {
        return getFileInAceso(ALL_CLASSES_DIR, varDirName, ALL_CLASSES_JAR)
    }

    @Override
    File getOutJar() {
        return null
    }

    @Override
    void process() {
        //do noting for now
    }


}
