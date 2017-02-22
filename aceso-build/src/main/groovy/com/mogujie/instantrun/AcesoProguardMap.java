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

package com.mogujie.instantrun;

import com.mogujie.aceso.util.FileUtils;
import org.gradle.api.GradleException;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The aceso's proguard map.
 *
 * @author wangzhi
 */
public class AcesoProguardMap {

    private HashMap<String, ClassData> classesMap;

    private int nowClassIndex;

    private int nowMtdIndex;

    private int nowFieldIndex;

    private int classMax;

    private int mtdMax;

    private int fieldMax;


    public class ClassData {
        Integer index;
        HashMap<String, Integer> methodMap;
        HashMap<String, Integer> fieldMap;

        ClassData(Integer index, HashMap<String, Integer> methodMap, HashMap<String, Integer> fieldMap) {
            this.index = index;
            this.methodMap = methodMap;
            this.fieldMap = fieldMap;
        }

        public int getIndex() {
            return index;
        }

        public void addMtd(String mtdSig, int index) {
            methodMap.put(mtdSig, index);
        }

        public Integer getMtdIndex(String mtdSig) {
            return methodMap.get(mtdSig);
        }

        public void addField(String fieldSig, int index) {
            fieldMap.put(fieldSig, index);
        }

        public Integer getFieldIndex(String fieldSig) {
            return fieldMap.get(fieldSig);
        }
    }


    class MapEntry {
        String name;
        int index;

        MapEntry(String name, int index) {
            this.name = name;
            this.index = index;
        }
    }

    private AcesoProguardMap() {
        reset();
    }

    private static class SINGLETON {
        private final static AcesoProguardMap instance = new AcesoProguardMap();
    }

    public static AcesoProguardMap instance() {
        return SINGLETON.instance;
    }

    public void putClass(String classNameInAsm) {
        if (classesMap.get(classNameInAsm) == null) {
            nowClassIndex++;
            putClass(classNameInAsm, classMax + nowClassIndex);
        }
    }

    public ClassData putClass(String classNameInAsm, int index) {
        ClassData classData = new ClassData(index,
                new HashMap<String, Integer>(), new HashMap<String, Integer>());
        classesMap.put(classNameInAsm, classData);
        return classData;
    }

    public void putMethod(String classNameInAsm, String mtdSig) {
        ClassData classData = classesMap.get(classNameInAsm);
        if (classData == null) {
            throw new RuntimeException("can not find class : " + classNameInAsm);
        }
        Integer oriIndex = classData.getMtdIndex(mtdSig);
        if (oriIndex == null) {
            nowMtdIndex++;
            putMethod(classData, mtdSig, mtdMax + nowMtdIndex);
        }
    }

    public void putMethod(ClassData classData, String mtdSig, int index) {
        classData.addMtd(mtdSig, index);
    }


    public int getClassIndex(String classNameInAsm) {
        ClassData classData = classesMap.get(classNameInAsm);
        return classData == null ? -1 : classData.getIndex();
    }

    public int getMtdIndex(String classNameInAsm, String mtdSig) {
        ClassData classData = classesMap.get(classNameInAsm);
        return classData == null ? -1 : classData.getMtdIndex(mtdSig);
    }


    @Deprecated
    public void putField(String fieldSig) {

    }

    @Deprecated
    public void putField(String fieldSig, int index) {

    }

    public void readMapping(File mappingFile) throws IOException {
        reset();
        LineNumberReader reader = new LineNumberReader(
                new BufferedReader(
                        new FileReader(mappingFile)));
        boolean hashReadHeader = true;
        int tempClassMax = 0;
        int tempMtdMax = 0;
        int tempFieldMax = 0;
        ClassData nowClassData = null;
        while (true) {
            String line = reader.readLine();

            if (line == null) {
                break;
            }
            line = line.trim();

            if (!hashReadHeader) {
                String[] strings = line.split(":");
                if (strings.length != 3) {
                    throw new GradleException("mapping's header is wrong,except is classNum:methodNum:fieldNum");
                }
                tempClassMax = Integer.parseInt(strings[0].trim());
                tempMtdMax = Integer.parseInt(strings[1].trim());
                tempFieldMax = Integer.parseInt(strings[0].trim());
                hashReadHeader = true;
                continue;
            }

            // Is it a non-comment line?
            if (!line.startsWith("#")) {
                // Is it a class mapping or a class member mapping?
                if (line.endsWith(":")) {
                    MapEntry mapEntry = getEntryFromLine(line);
                    nowClassData = putClass(mapEntry.name, mapEntry.index);
                } else if (nowClassData != null) {
                    MapEntry mapEntry = getEntryFromLine(line);
                    if (line.contains("(")) {
                        putMethod(nowClassData, mapEntry.name, mapEntry.index);
                    } else {
                        putField(mapEntry.name, mapEntry.index);
                    }
                }
            }

        }

        classMax = tempClassMax;
        mtdMax = tempMtdMax;
        fieldMax = tempFieldMax;

    }


    private MapEntry getEntryFromLine(String line) {
        int arrowIndex = line.indexOf("->");
        if (arrowIndex < 0) {
            return null;
        }
        boolean isClass = line.endsWith(":");
        String oriName = line.substring(0, arrowIndex).trim();
        String indexStr = line.substring(arrowIndex + 2, isClass ? line.length() - 1 : line.length()).trim();
        return new MapEntry(oriName, Integer.parseInt(indexStr));
    }

    public void printMapping(final File mappingFile) {
        if (mappingFile == null) {
            return;
        }
        FileUtils.initParentFile(mappingFile);


        final StringBuilder sb = new StringBuilder();
        sb.append(nowClassIndex + ":" + nowMtdIndex + ":" + nowFieldIndex + "\n");
        eachMap(classesMap, new EachMapListenr<String, ClassData>() {

            @Override
            public void invoke(String classNameInAsm, ClassData classData) {
                sb.append(classNameInAsm);
                sb.append(" -> ");
                sb.append(classData.index);
                sb.append(":\n");


                eachMap(classData.fieldMap, new EachMapListenr<String, Integer>() {
                    @Override
                    public void invoke(String fieldSig, Integer fieldIndex) {
                        sb.append("    ");
                        sb.append(fieldSig);
                        sb.append(" -> ");
                        sb.append(fieldIndex);
                        sb.append("\n");
                    }

                });


                eachMap(classData.methodMap, new EachMapListenr<String, Integer>() {
                    @Override
                    public void invoke(String mtdSig, Integer mtdIndex) {
                        sb.append("    ");
                        sb.append(mtdSig);
                        sb.append(" -> ");
                        sb.append(mtdIndex);
                        sb.append("\n");
                    }

                });

            }

        });
        FileUtils.writeFile(mappingFile, sb.toString().getBytes());
    }


    public void reset() {
        classesMap = new HashMap();
        nowClassIndex = 0;
        nowMtdIndex = 0;
        nowFieldIndex = 0;
        classMax = 0;
        mtdMax = 0;
        fieldMax = 0;
    }


    public ClassData getClassData(String classNameInAsm) {
        return classesMap.get(classNameInAsm);
    }

    public int getNowClassIndex() {
        return nowClassIndex;
    }

    public int getNowMtdIndex() {
        return nowMtdIndex;
    }


    public interface EachMapListenr<K, V> {
        void invoke(K key, V value);
    }

    public static void eachMap(Map map, EachMapListenr listener) {
        Iterator iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            Object key = entry.getKey();
            Object val = entry.getValue();
            listener.invoke(key, val);
        }
    }
}
