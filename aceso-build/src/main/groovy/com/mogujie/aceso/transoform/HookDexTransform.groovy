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
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder
import com.android.build.gradle.internal.transforms.DexTransform
import com.android.build.gradle.internal.transforms.JarMerger
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.mogujie.aceso.processor.ClassProcessor
import com.mogujie.aceso.util.GradleUtil
import com.mogujie.aceso.util.Log
import org.gradle.api.Project
/**
 * This class hook the real dex transform,
 * and process the class file.
 *
 * @author wangzhi
 */
public class HookDexTransform extends HookTransform {


    HookDexTransform(Project project, def variant, Transform transform, ClassProcessor processor) {
        super(project, variant, transform, processor)
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, IOException, InterruptedException {

        if (processor == null) {
            transform.transform(transformInvocation)
            return
        }

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

        //process the mergedJar
        processor.process()

        //invoke the original transform method
        TransformInvocationBuilder builder = new TransformInvocationBuilder(transformInvocation.getContext());
        builder.addInputs(jarFileToInputs(processor.getOutJar()))
        builder.addOutputProvider(transformInvocation.getOutputProvider())
        builder.addReferencedInputs(transformInvocation.getReferencedInputs())
        builder.addSecondaryInputs(transformInvocation.getSecondaryInputs())
        builder.setIncrementalMode(transformInvocation.isIncremental())
        transform.transform(builder.build())

    }

    /**
     * change the jar file to TransformInputs
     */
    Collection<TransformInput> jarFileToInputs(File jarFile) {
        TransformInput transformInput = new TransformInput() {
            @Override
            Collection<JarInput> getJarInputs() {
                JarInput jarInput = new JarInput() {
                    @Override
                    Status getStatus() {
                        return Status.ADDED
                    }

                    @Override
                    String getName() {
                        return jarFile.getName().substring(0,
                                jarFile.getName().length() - ".jar".length())
                    }

                    @Override
                    File getFile() {
                        return jarFile
                    }

                    @Override
                    Set<QualifiedContent.ContentType> getContentTypes() {
                        return HookDexTransform.this.getInputTypes()
                    }

                    @Override
                    Set<QualifiedContent.Scope> getScopes() {
                        return HookDexTransform.this.getScopes()
                    }
                }
                return ImmutableList.of(jarInput)
            }


            @Override
            Collection<DirectoryInput> getDirectoryInputs() {
                return ImmutableList.of()
            }
        }
        return ImmutableList.of(transformInput)
    }


    public static final HookTransform.TransformBuilder BUILDER = new HookTransform.TransformBuilder() {

        HookTransform build(Project project, Object variant,
                            Transform transform, ClassProcessor processor) {
            return new HookDexTransform(project, variant, transform, processor)
        }

        boolean isExactTransform(Transform transform) {
            return (((transform instanceof DexTransform) || transform.getName().equals("dex"))
                    && !(transform instanceof HookDexTransform))

        }

    }


}



