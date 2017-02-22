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

package com.mogujie.aceso.util

/**
 * This class hold the proguard map.
 *
 * @author wangzhi
 */

public class ProguardUtil {


    private ProguardUtil() {
    }

    private static class SINGLETON {
        private final static ProguardUtil instance = new ProguardUtil();
    }

    public static ProguardUtil instance() {
        return SINGLETON.instance;
    }
    private HashMap<String, String> proguardMap = new HashMap<>();

    HashMap<String, String> getProguardMap() {
        return proguardMap
    }

    /**
     * read mappingFile,and store in proguardMap.
     */
    public void initProguardMap(File mappingFile) {

        mappingFile.readLines().each { line ->
            line = line.trim();
            // Is it a non-comment line?
            if (!line.startsWith("#")) {
                // Is it a class mapping or a class member mapping?
                if (line.endsWith(":")) {
                    int arrowIndex = line.indexOf("->");
                    if (arrowIndex < 0) {
                        return null;
                    }
                    int colonIndex = line.indexOf(':', arrowIndex + 2);
                    if (colonIndex < 0) {
                        return null;
                    }
                    // Extract the elements.
                    String className = line.substring(0, arrowIndex).trim();
                    String newClassName = line.substring(arrowIndex + 2, colonIndex).trim();
                    proguardMap.put(newClassName.replace(".", "/") + ".class", className.replace(".", "/") + ".class")

                }

            }
        }

    }
}
