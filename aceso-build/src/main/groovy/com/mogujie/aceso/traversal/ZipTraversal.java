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

package com.mogujie.aceso.traversal;

import com.mogujie.aceso.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by wangzhi on 16/10/18.
 */
public class ZipTraversal {

    public static void traversal(ZipFile zipFile, Callback callback) {
        try {
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                callback.oneEntry(entry, FileUtils.toByteArray(zipFile.getInputStream(entry)));
            }
        } catch (IOException e) {
            e.printStackTrace();
            FileUtils.closeQuietly(zipFile);
        }

    }

    public static void traversal(File file, Callback callback) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file);
            traversal(zipFile, callback);
        } catch (IOException e) {
            e.printStackTrace();
            FileUtils.closeQuietly(zipFile);
        }
    }

    public interface Callback {
        void oneEntry(ZipEntry entry, byte[] bytes);
    }
}
