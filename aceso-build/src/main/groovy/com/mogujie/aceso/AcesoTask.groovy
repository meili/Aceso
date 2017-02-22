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
import com.mogujie.aceso.util.Log
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * A empty task whose name is acesoXXX and dependsOn assemble task.
 *
 * @author wangzhi
 */

public class AcesoTask extends DefaultTask {

    @TaskAction
    void taskExec() {
        Log.i("execute aceso fix successful!")
    }

    public static class HotFixAction implements org.gradle.api.Action<AcesoTask> {
        String varName

        HotFixAction(String varName) {
            this.varName = varName
        }

        @Override
        void execute(AcesoTask hotfixTask) {
            def assembleTask = hotfixTask.project.tasks.findByName("assemble" + varName)
            hotfixTask.dependsOn assembleTask
        }

    }
}
