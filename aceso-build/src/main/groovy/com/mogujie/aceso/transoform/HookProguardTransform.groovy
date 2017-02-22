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

package com.mogujie.aceso.transoform

import com.android.build.api.transform.*
import com.android.build.gradle.internal.transforms.JarMerger
import com.android.build.gradle.internal.transforms.ProGuardTransform
import com.google.common.collect.Lists
import com.mogujie.aceso.processor.ClassProcessor
import com.mogujie.aceso.util.GradleUtil
import com.mogujie.aceso.util.Log
import org.gradle.api.Project
/**
 * This class hook the real proguard transform,
 * and process the class file.
 *
 * @author wangzhi
 */
public class HookProguardTransform extends HookTransform {

    HookProguardTransform(Project project, def variant, Transform transform, ClassProcessor processor) {
        super(project, variant, transform, processor)
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, IOException, InterruptedException {
        Log.i("start exec hook proguard transform..")
        List<JarInput> jarInputs = Lists.newArrayList();
        List<DirectoryInput> dirInputs = Lists.newArrayList();

        for (TransformInput input : transformInvocation.getInputs()) {
            jarInputs.addAll(input.getJarInputs());
        }

        for (TransformInput input : transformInvocation.getInputs()) {
            dirInputs.addAll(input.getDirectoryInputs());
        }
        //init dir
        processor.prepare()

        JarMerger jarMerger = GradleUtil.getClassJarMerger(processor.getMergedJar())

        jarInputs.each { jar ->
            Log.i("add jar " + jar.getFile())
            jarMerger.addJar(jar.getFile())
        }
        dirInputs.each { dir ->
            Log.i("add dir " + dir.getFile())
            jarMerger.addFolder(dir.getFile())
        }

        jarMerger.close()

        processor.process()

        transform.transform(transformInvocation)

    }


    public static final HookTransform.TransformBuilder BUILDER = new HookTransform.TransformBuilder() {

        HookTransform build(Project project, Object variant,
                            Transform transform, ClassProcessor processor) {
            return new HookProguardTransform(project, variant, transform, processor)
        }

        boolean isExactTransform(Transform transform) {
            return (((transform instanceof ProGuardTransform) || transform.getName().equals("proguard"))
                    && !(transform instanceof HookProguardTransform))

        }

    }
}



