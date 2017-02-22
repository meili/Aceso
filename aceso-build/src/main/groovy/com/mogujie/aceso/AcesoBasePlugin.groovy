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

import com.android.build.gradle.internal.pipeline.TransformTask
import com.mogujie.aceso.util.FileUtils
import com.mogujie.aceso.util.GradleUtil
import com.mogujie.aceso.util.Log
import com.mogujie.aceso.util.ProguardUtil
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The base plugin
 *
 * @author wangzhi
 */

public abstract class AcesoBasePlugin implements Plugin<Project> {

    public static def MATCHER_R = '''.*/R\\$.*\\.class|.*/R\\.class'''

    Project project
    Extension config
    List<String> blackList
    List<String> whiteList

    @Override
    void apply(Project project) {
        this.project = project
        project.extensions.create("Aceso", Extension);

        project.afterEvaluate {
            initExtensions()
            Log.logLevel = config.logLevel
            if (config.enable) {
                //do some init and check
                assignDefaultValue()
                checkNecessaryFile()
                initBlacklist()
                HookWrapper.filter = initFilter()

                realApply()
            }
        }
    }

    protected void realApply() {

    }

    protected void initExtensions() {
        config = project.extensions.findByName("Aceso") as Extension
    }

    protected void initBlacklist() {
        File blackListFile;
        blackList = new ArrayList<>()
        whiteList = new ArrayList<>()
        blackList.add("com/android/tools/fd/runtime")
        if (FileUtils.isStringEmpty(config.blackListPath)) {
            blackListFile = new File(project.projectDir, 'aceso-blacklist.txt')
        } else {
            blackListFile = new File(config.blackListPath)
        }
        Log.i("blackList file is " + blackListFile.absolutePath)

        if (blackListFile.exists()) {
            blackListFile.eachLine { line ->
                String lineNoSpace = line.trim()
                if (lineNoSpace.length() > 0 && !lineNoSpace.startsWith("#")) {
                    if (lineNoSpace.startsWith("!")) {
                        whiteList.add(lineNoSpace.substring(1, lineNoSpace.length()))
                    } else {
                        blackList.add(lineNoSpace)
                    }
                }
            }
        }
    }

    protected void addProguardKeepRule(def variant) {

        if (GradleUtil.isProguardOpen(variant)) {
            TransformTask proguardTask = GradleUtil.getProguardTask(project, variant.name.capitalize())
            if (proguardTask == null) {
                throw new GradleException("minifyEnabled is true,but not found proguard task.")
            }
            proguardTask.doFirst {
                File customProFile = GradleUtil.getFileInAceso(project, "proguard",
                        variant.getDirName(), "aceso-keep.pro")
                FileUtils.initFile(customProFile)
                customProFile.write(Constant.KEEP_RULE)
                Log.i("exist : " + customProFile.exists())
                // Add this proguard settings file to the list
                variant.getBuildType().buildType.proguardFiles(customProFile)
                Log.i("proguard files is ${variant.getBuildType().buildType.getProguardFiles()}")
            }
        }

    }

    protected void assignDefaultValue() {
        if (FileUtils.isStringEmpty(config.acesoMapping)) {
            config.acesoMapping = new File(project.projectDir, Constant.ACESO_MAPPING).absolutePath
        }
    }

    protected void checkNecessaryFile() {

    }

    public HookWrapper.InstrumentFilter initFilter() {

        return new HookWrapper.InstrumentFilter() {
            @Override
            boolean accept(String name) {
                if (!name.endsWith(".class")) {
                    return false
                }

                if (ProguardUtil.instance().getProguardMap().size() > 0) {
                    String realName = ProguardUtil.instance().getProguardMap().get(name)
                    name = realName == null ? name : realName
                }

                if (name.endsWith("BuildConfig.class") || name ==~ MATCHER_R) {
                    return false
                }

                for (String str : whiteList) {
                    if (name.startsWith(str)) {
                        return true
                    }
                }

                for (String str : blackList) {
                    if (name.startsWith(str)) {
                        return false
                    }
                }
                return true
            }
        }
    }

}
