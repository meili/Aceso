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
import java.lang.reflect.Field

/**
 * A reflect utils .
 *
 * @author wangzhi
 */
public class ReflectUtils {

    public static void setField(
            Object targetObject,
            Object value,
            String fieldName) {

        try {
            Field declaredField = getField0(targetObject.getClass(), fieldName);
            declaredField.set(targetObject, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    public static Object getField(
            Object targetObject,
            String fieldName) {
        try {
            Field declaredField = getField0(targetObject.getClass(), fieldName);
            return declaredField.get(targetObject);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    private static Field getField0(Class target, String name) {
        Field declareField = getFieldByName(target, name);
        if (declareField == null) {
            throw new RuntimeException(new NoSuchElementException(name));
        }
        declareField.setAccessible(true);
        return declareField;
    }


    private static Field getFieldByName(Class<?> aClass, String name) {

        Class<?> currentClass = aClass;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                // ignored.
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }



}
