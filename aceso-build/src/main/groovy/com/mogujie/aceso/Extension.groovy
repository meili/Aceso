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

/**
 * The DSL configuration.
 *
 * @author wangzhi
 */

public class Extension {

    /*-------------Configuration for AcesoBasePlugin-------------*/

    /**
     * The log level ,value range from 0-3,low to high.
     */
    public int logLevel = 2

    /**
     * Enable the plugin.
     */
    public boolean enable = true

    /**
     * The aceso mapping.
     * For host plugn,it is use for keeping this aceso-proguard's compilation result.
     * For fix plugin,it is use for generate patch apk.
     */
    public String acesoMapping

    /*-------------Configuration for AcesoHostPlugin-------------*/

    /**
     * Open the incrument in debug compile.
     */
    public boolean instrumentDebug = false

    /**
     *  BlackList for class
     */
    public String blackListPath


    /*-------------Configuration for AcesoFixPlugin-------------*/

    /**
     * the instrument.jar's path
     */
    public String instrumentJar

    /**
     * the all-classes.jar's path
     */
    public String allClassesJar

    /**
     * Is method level fix? If true,you need add annotation
     *  com.android.annotations.FixMtd to the method that you want to fix.
     */
    public boolean methodLevelFix = true

}
