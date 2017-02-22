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

import com.google.common.base.Joiner
import org.gradle.api.Project

import java.security.MessageDigest

/**
 * A file utils.
 *
 * @author wangzhi
 */
public class FileUtils {

    private static final Joiner PATH_JOINER = Joiner.on(File.separatorChar);

    public static boolean isStringEmpty(String s) {
        return (s == null || s.length() == 0)
    }

    public static String joinPath(String... paths) {
        PATH_JOINER.join(paths)
    }

    public static File joinFile(File file, String... paths) {
        return new File(file, joinPath(paths))
    }

    public static void writeFile(File file, byte[] bytes) {
        initParentFile(file)
        file.withDataOutputStream { output ->
            output.write(bytes, 0, bytes.length)
            output.close()
        }
    }

    public static String openFileToString(File file) {
        byte[] bytes = new byte[1024]
        String str = ""
        if (!file.exists()) {
            return str
        }
        file.withInputStream { input ->
            int readCount = -1
            while ((readCount = input.read(bytes)) != -1) {
                str += new String(bytes, 0, readCount)
            }
            input.close()
        }
        return str
    }


    public static void renameFile(File originFile, File targetFile) {
        if (!originFile.renameTo(targetFile)) {
            throw new RuntimeException("${originFile} rename to ${targetFile} failed ");
        }
    }


    public static byte[] toByteArray(final InputStream input) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8024];
        int n = 0;
        long count = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return output.toByteArray();
    }


    public static void initParentFile(File file) {
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }
    }

    public static String md5(File file) {
        return MessageDigest.getInstance("MD5").digest(file.bytes).encodeHex().toString()
    }


    public static boolean checkFile(File file) {
        return file != null && file.exists()
    }

    public static boolean checkFile(String path) {
        File file = new File(path)
        return file != null && file.exists()
    }

    public static void clearDir(File dir) {
        if (dir.exists()) {
            dir.deleteDir()
        }
        dir.mkdirs()
    }

    public static initFile(File file) {
        initParentFile(file)
        file.delete()
        return file
    }

    public static void closeQuietly(Closeable co) {
        try {
            if (co != null) {
                co.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void copy(Project project, File src, File dstDir) {
        copy(project, src, dstDir, null)
    }

    public static void copy(Project project, File src, File dstDir, String newName) {
        FileUtils.clearDir(dstDir)
        project.copy {
            from src
            into dstDir
            if (newName != null) {
                rename { String fileName ->
                    return newName
                }
            }
            println "copy from ${src.absolutePath} to ${dstDir.absolutePath}"
        }
    }

}
