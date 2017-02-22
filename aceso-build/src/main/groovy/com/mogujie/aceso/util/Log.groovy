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
 * Created by wangzhi on 16/4/6.
 */
class Log {
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    /*
        0 <-> only error
        1 <-> ${0} and warn
        2 <-> ${1} and info
        3 <-> ${2} and verbose

     */
    public static int logLevel = 2
    static IPrintCore printCore = new IPrintCore() {

        @Override
        void v(Object arg) {
            println(arg)
        }

        @Override
        void i(Object arg) {
            println(arg)
        }

        @Override
        void w(Object arg) {
            println(ANSI_YELLOW + arg + ANSI_RESET)
        }

        @Override
        void e(Object arg) {
            println(ANSI_RED + arg + ANSI_RESET)
        }

        @Override
        void f(Object arg, IFileRecoder recoder) {
            recoder.write(arg)
        }
    }

    public static interface IPrintCore {
        public void v(Object arg)

        public void i(Object arg)

        public void w(Object arg)

        public void e(Object arg)

        public void f(Object arg, IFileRecoder recoder)
    }

    public static interface IFileRecoder {
        void write(Object arg)
    }

    public static void e(Object arg) {
        if (logLevel >= 0) {
            printCore.e(arg)
        }
    }

    public static void w(Object arg) {
        if (logLevel >= 1) {
            printCore.w(arg)
        }
    }

    public static void i(Object arg) {
        if (logLevel >= 2) {
            printCore.i(arg)
        }
    }

    public static void v(Object arg) {
        if (logLevel >= 3) {
            printCore.i(arg)
        }
    }

    public static void f(Object arg, IFileRecoder writer) {
        printCore.f(arg, writer)
    }
}
