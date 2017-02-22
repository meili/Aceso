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

package com.mogujie.aceso.demo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;

import com.mogujie.aceso.Aceso;

import java.io.File;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void fix(View view) {
        File patchFile = new File(Environment.getExternalStorageDirectory(), "fix.apk");
        if (!patchFile.exists()) {
            Toast.makeText(this, "hotfix file not exist!", Toast.LENGTH_SHORT).show();
            return;
        }
        File optDir = new File(this.getFilesDir(), "fix_opt");
        optDir.mkdirs();
        new Aceso().installPatch(optDir, patchFile);
    }

    public void test(View view) {
        Toast.makeText(this, "not fix!", Toast.LENGTH_SHORT).show();
    }
}
