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

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.gradle.internal.pipeline.TransformTask
import com.mogujie.aceso.processor.ClassProcessor
import com.mogujie.aceso.util.Log
import com.mogujie.aceso.util.ReflectUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener

/**
 * Created by wangzhi on 17/2/8.
 */
public abstract class HookTransform extends Transform {

    Project project

    String varName

    String varDirName

    def variant

    Transform transform

    ClassProcessor processor

    HookTransform(Project project, def variant, Transform transform, ClassProcessor processor) {
        this.transform = transform
        this.project = project
        this.variant = variant
        this.varName = variant.name.capitalize()
        this.varDirName = variant.getDirName()
        this.processor = processor
    }

    public interface TransformBuilder {
        HookTransform build(Project project, Object variant,
                            Transform transform, ClassProcessor processor)

        boolean isExactTransform(Transform transform)

    }

    /**
     * Replace specified task 's transform with HookTransform
     */

    public static void injectTransform(Project project,
                                       def variant, ClassProcessor processor, TransformBuilder builder) {

        project.getGradle().getTaskGraph().addTaskExecutionGraphListener(new TaskExecutionGraphListener() {
            @Override
            public void graphPopulated(TaskExecutionGraph taskGraph) {
                for (Task task : taskGraph.getAllTasks()) {
                    if (task.getProject().equals(project)
                            && task instanceof TransformTask
                            && task.name.toLowerCase().contains(variant.name.toLowerCase())) {
                        if (builder.isExactTransform(((TransformTask) task).getTransform())) {
                            Log.w("find transform. class: " + task.transform.getClass() + ". task name: " + task.name)
                            HookTransform hookTransform = builder.build(project, variant, task.transform, processor)
                            ReflectUtils.setField(task, hookTransform, "transform")
                            break;
                        }
                    }
                }
            }
        }

        );
    }

    @Override
    Object invokeMethod(String name, Object args) {
        Log.i("invoke missing method : " + name + "  " + args)
        return transform.invokeMethod(name, args)
    }


    @Override
    String getName() {
        return transform.getName()
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return transform.getInputTypes()
    }


    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return transform.getScopes()
    }

    @Override
    boolean isIncremental() {
        return transform.isIncremental()
    }

}
