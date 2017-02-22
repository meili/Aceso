/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mogujie.instantrun;

import com.google.common.collect.ImmutableList;
import com.mogujie.aceso.util.Log;
import com.mogujie.aceso.util.FileUtils;
import org.gradle.api.GradleException;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class IncrementalVisitor extends ClassVisitor {


    public enum OutputType {
        /**
         * provide instrumented classes that can be hot swapped at runtime with an override class.
         */
        INSTRUMENT,
        /**
         * provide override classes that be be used to hot swap an instrumented class.
         */
        OVERRIDE
    }

    public static final String PACKAGE = "com/android/tools/fd/runtime";
    public static final String ABSTRACT_PATCHES_LOADER_IMPL =
            PACKAGE + "/AbstractPatchesLoaderImpl";
    public static final String APP_PATCHES_LOADER_IMPL = PACKAGE + "/AppPatchesLoaderImpl";

    protected static final Type INSTANT_RELOAD_EXCEPTION =
            Type.getObjectType(PACKAGE + "/InstantReloadException");
    protected static final Type RUNTIME_TYPE =
            Type.getObjectType(PACKAGE + "/AndroidInstantRuntime");
    public static final Type DISABLE_ANNOTATION_TYPE =
            Type.getObjectType("com/android/tools/ir/api/DisableInstantRun");
    public static final Type FIXMTD_ANNOTATION_TYPE =
            Type.getObjectType("com/android/annotations/FixMtd");


    protected static final boolean TRACING_ENABLED = Boolean.getBoolean("FDR_TRACING");

    public static final Type CHANGE_TYPE = Type.getObjectType(PACKAGE + "/IncrementalChange");
    public static final Type MTD_MAP_TYPE = Type.getObjectType(PACKAGE + "/InstantFixClassMap");

    protected String visitedClassName;
    protected String visitedSuperName;

    protected final ClassNode classNode;

    protected final List<ClassNode> parentNodes;

    /**
     * Enumeration describing a method of field access rights.
     */
    protected enum AccessRight {
        PRIVATE, PACKAGE_PRIVATE, PROTECTED, PUBLIC;


        static AccessRight fromNodeAccess(int nodeAccess) {
            if ((nodeAccess & Opcodes.ACC_PRIVATE) != 0) return PRIVATE;
            if ((nodeAccess & Opcodes.ACC_PROTECTED) != 0) return PROTECTED;
            if ((nodeAccess & Opcodes.ACC_PUBLIC) != 0) return PUBLIC;
            return PACKAGE_PRIVATE;
        }
    }

    public IncrementalVisitor(
             ClassNode classNode,
             List<ClassNode> parentNodes,
             ClassVisitor classVisitor) {
        super(Opcodes.ASM5, classVisitor);
        this.classNode = classNode;
        this.parentNodes = parentNodes;
        Log.v(getClass().getSimpleName() + ": Visiting " + classNode.name);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        return super.visitMethod(access, name, desc, signature, exceptions);
    }


    protected static String getRuntimeTypeName( Type type) {
        return "L" + type.getInternalName() + ";";
    }


    FieldNode getFieldByName( String fieldName) {
        FieldNode fieldNode = getFieldByNameInClass(fieldName, classNode);
        Iterator<ClassNode> iterator = parentNodes.iterator();
        while (fieldNode == null && iterator.hasNext()) {
            ClassNode parentNode = iterator.next();
            fieldNode = getFieldByNameInClass(fieldName, parentNode);
        }
        return fieldNode;
    }


    protected static FieldNode getFieldByNameInClass(
             String fieldName,  ClassNode classNode) {
        //noinspection unchecked ASM api.
        List<FieldNode> fields = classNode.fields;
        for (FieldNode field : fields) {
            if (field.name.equals(fieldName)) {
                return field;
            }
        }
        return null;
    }


    protected MethodNode getMethodByName(String methodName, String desc) {
        MethodNode methodNode = getMethodByNameInClass(methodName, desc, classNode);
        Iterator<ClassNode> iterator = parentNodes.iterator();
        while (methodNode == null && iterator.hasNext()) {
            ClassNode parentNode = iterator.next();
            methodNode = getMethodByNameInClass(methodName, desc, parentNode);
        }
        return methodNode;
    }


    protected static MethodNode getMethodByNameInClass(String methodName, String desc, ClassNode classNode) {
        //noinspection unchecked ASM API
        List<MethodNode> methods = classNode.methods;
        for (MethodNode method : methods) {
            if (method.name.equals(methodName) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }

    protected static void trace( GeneratorAdapter mv,  String s) {
        mv.push(s);
        mv.invokeStatic(Type.getObjectType(PACKAGE + "/AndroidInstantRuntime"),
                Method.getMethod("void trace(String)"));
    }


    protected static void trace( GeneratorAdapter mv, int argsNumber) {
        StringBuilder methodSignature = new StringBuilder("void trace(String");
        for (int i = 0; i < argsNumber - 1; i++) {
            methodSignature.append(", String");
        }
        methodSignature.append(")");
        mv.invokeStatic(Type.getObjectType(PACKAGE + "/AndroidInstantRuntime"),
                Method.getMethod(methodSignature.toString()));
    }

    /**
     * Simple Builder interface for common methods between all byte code visitors.
     */
    public interface VisitorBuilder {

        IncrementalVisitor build( ClassNode classNode,
                                  List<ClassNode> parentNodes,  ClassVisitor classVisitor);


        String getMangledRelativeClassFilePath( String originalClassFilePath);


        OutputType getOutputType();
    }


    /**
     * Defines when a method access flags are compatible with InstantRun technology.
     * <p/>
     * - If the method is a bridge method, we do not enable it for instantReload.
     * it is most likely only calling a twin method (same name, same parameters).
     * - if the method is abstract or native, we don't add a redirection.
     *
     * @param access the method access flags
     * @return true if the method should be InstantRun enabled, false otherwise.
     */
    protected static boolean isAccessCompatibleWithInstantRun(int access) {
        return (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_BRIDGE | Opcodes.ACC_NATIVE)) == 0;
    }


    public static boolean instrumentClass(
            ZipEntry entry,
            ZipFile zipFile,
            ZipOutputStream zos,
             VisitorBuilder visitorBuilder, boolean isHotfix) throws IOException {
        byte[] classBytes = FileUtils.toByteArray(zipFile.getInputStream(entry));
        ClassReader classReader = new ClassReader(classBytes);
        // override the getCommonSuperClass to use the thread context class loader instead of
        // the system classloader. This is useful as ASM needs to load classes from the project
        // which the system classloader does not have visibility upon.
        // TODO: investigate if there is not a simpler way than overriding.
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(final String type1, final String type2) {
                Class<?> c, d;
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                try {
                    c = Class.forName(type1.replace('/', '.'), false, classLoader);
                    d = Class.forName(type2.replace('/', '.'), false, classLoader);
                } catch (ClassNotFoundException e) {
                    // This may happen if we're processing class files which reference APIs not
                    // available on the target device. In this case return a dummy value, since this
                    // is ignored during dx compilation.
                    return "instant/run/NoCommonSuperClass";
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (c.isAssignableFrom(d)) {
                    return type1;
                }
                if (d.isAssignableFrom(c)) {
                    return type2;
                }
                if (c.isInterface() || d.isInterface()) {
                    return "java/lang/Object";
                } else {
                    do {
                        c = c.getSuperclass();
                    } while (!c.isAssignableFrom(d));
                    return c.getName().replace('.', '/');
                }
            }
        };

        ClassNode classNode = new TransformAccessClassNode();
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);

        // when dealing with interface, we just copy the inputFile over without any changes unless
        // this is a package private interface.
        AccessRight accessRight = AccessRight.fromNodeAccess(classNode.access);
        ZipEntry nowEntry;
        if (isHotfix) {
            String name = entry.getName();
            name = name.substring(0, name.lastIndexOf(".class"));
            nowEntry = new ZipEntry(name + "$override" + ".class");
        } else {
            nowEntry = new ZipEntry(entry.getName());
        }

        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            if (visitorBuilder.getOutputType() == OutputType.INSTRUMENT) {
                // don't change the name of interfaces.
                zos.putNextEntry(nowEntry);
                if (accessRight == AccessRight.PACKAGE_PRIVATE) {
                    classNode.access = classNode.access | Opcodes.ACC_PUBLIC;
                    classNode.accept(classWriter);
                    zos.write(classWriter.toByteArray());
                } else {
                    // just copy the input file over, no change.
                    zos.write(classBytes);
                }
                zos.closeEntry();
                return true;
            } else {
                return false;
            }
        }

        List<ClassNode> parentsNodes = parseParents(zipFile, classNode);

        IncrementalVisitor visitor = visitorBuilder.build(classNode, parentsNodes, classWriter);
        classNode.accept(visitor);

        zos.putNextEntry(nowEntry);
        zos.write(classWriter.toByteArray());
        zos.closeEntry();

        if (isHotfix) {
            IncrementalChangeVisitor changeVisitor = (IncrementalChangeVisitor) visitor;
            if (changeVisitor.superMethods.size() > 0) {
                if (parentsNodes.size() <= 0) {
                    throw new GradleException("not found " + changeVisitor.visitedClassName + " 's parents.");
                }
                SuperHelperVisitor superHelperVisitor = new SuperHelperVisitor(Opcodes.ASM5, changeVisitor, parentsNodes.get(0));
                superHelperVisitor.start();
                String newName = entry.getName();
                newName = newName.substring(0, newName.lastIndexOf(".class"));
                newName += "$helper.class";
                ZipEntry zipEntry = new ZipEntry(newName);
                zos.putNextEntry(zipEntry);
                zos.write(superHelperVisitor.toByteArray());
                zos.closeEntry();
            }

        }
        return true;
    }



    private static List<ClassNode> parseParents(
             ZipFile zipFile,  final ClassNode classNode) throws IOException {
        List<ClassNode> parentNodes = new ArrayList<ClassNode>();
        String currentParentName = classNode.superName;

        while (currentParentName != null) {
            ZipEntry zipEntry = zipFile.getEntry(currentParentName + ".class");
            if (zipEntry != null) {
                InputStream parentFileClassReader = new BufferedInputStream(zipFile.getInputStream(zipEntry));
                ClassReader parentClassReader = new ClassReader(parentFileClassReader);
                ClassNode parentNode = new TransformAccessClassNode();
                parentClassReader.accept(parentNode, ClassReader.EXPAND_FRAMES);
                parentNodes.add(parentNode);
                currentParentName = parentNode.superName;
            } else {
                // May need method information from outside of the current project. Thread local class reader
                // should be the one
                try {
                    ClassReader parentClassReader = new ClassReader(
                            Thread.currentThread().getContextClassLoader().getResourceAsStream(
                                    currentParentName + ".class"));
                    ClassNode parentNode = new ClassNode();
                    parentClassReader.accept(parentNode, ClassReader.EXPAND_FRAMES);
                    parentNodes.add(parentNode);
                    currentParentName = parentNode.superName;

                } catch (IOException e) {
                    // Could not locate parent class. This is as far as we can go locating parents.
                    return ImmutableList.of();
                }
            }
        }
        return parentNodes;
    }


}
