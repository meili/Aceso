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

package com.mogujie.aceso;

import android.util.Log;

import com.android.tools.fd.runtime.InstantRunClassLoader;
import com.android.tools.fd.runtime.PatchesLoader;

import java.io.File;


/**
 * Aceso
 */

public class Aceso {

    private static final String TAG = "Aceso";

    /**
     * Install the patch file.
     *
     * @param optDir The optimized directory.
     * @param patchFile The patch file.
     * @return True if the patch is installed successfully.
     */
    public boolean installPatch(File optDir, File patchFile) {

        boolean result = true;

        try {
            ClassLoader clsLoader = new InstantRunClassLoader(patchFile.getAbsolutePath(), optDir,
                    getClass().getClassLoader());
            Class<?> aClass = Class.forName(
                    "com.android.tools.fd.runtime.AppPatchesLoaderImpl", false, clsLoader);
            PatchesLoader loader = (PatchesLoader) aClass.newInstance();
            String[] getPatchedClasses = (String[]) aClass
                    .getDeclaredMethod("getPatchedClasses").invoke(loader);

            Log.v(TAG, "Got the list of classes ");
            for (String getPatchedClass : getPatchedClasses) {
                Log.v(TAG, "class " + getPatchedClass);
            }

            if (!loader.load()) {
                result = false;
            }
        } catch (Throwable e) {
            result = false;
            Log.e(TAG, "Failed to install patch. ", e);
        }

        return result;
    }
}
