## Aceso

Aceso is a Android HotFix by optimizing the AOSP's "InstantRun HotSwap" solution, it is used to fix online bugs without a new APK publish.

[wiki](https://github.com/meili/Aceso/wiki)

[中文说明](README-zh.md)


## Features

- Support 4.x to 7.0 Android OS
- Perfect compatibility 
- Taking effect immediately after download without reboot
- Support fixing at method level
- Support adding new classes

## Limitations

- Current not support fixing static code block and constructors.
- Functions that shrinked/inlined by proguard are NOT repariable.
- Only the Method's body can be fixed.


## Usage
1.Add below codes in the outest project's build.gradle file

```groovy
buildscript {
    repositories {
        jcenter()
    }

    dependencies {
         classpath 'com.mogujie.aceso:aceso-build:0.0.3'
    }
}
```

2.Add below codes in the module's build.gradle

```groovy
apply plugin: 'AcesoHost'

dependencies {
    compile 'com.mogujie.aceso:aceso-android-lib:0.0.1'
}

```

Add below codes once you need aceso for Debug version.

```groovy
Aceso {
    instrumentDebug = true
}
```

3.Add below codes at the place after HotFix downloaded and Application's onAttachedBaseContext() or onCreate() 

```java
 new Aceso().installPatch(optDir, patchFile);
```
4.Pls. reserve the file folder "build/intermediates/aceso" under module directory at each HotFix publishment.
 
## Generate Patch
1.An additional Fix project is needed(pls. refer to [aceso-demo-fix](aceso-demo-fix))

2.Pls. add below codes in the outest prject's build.gradle:

```groovy
buildscript {
    repositories {
        jcenter()
    }

    dependencies {
         classpath 'com.mogujie.aceso:aceso-build:0.0.3'
    }
}
```

3.Pls. add below codes in the module's build.gradle：

```groovy
apply plugin: 'AcesoFix'

Aceso {
    // The HotFix is for method level once methodLevelFix set to true, i.e., only fix the designated method, the annotation @FixMtd should be added above the method to be fixed. Once set to false, the HotFix is for class level. 
    methodLevelFix = true
    // Below files are those aceso file folders whch mentioned in 4th step of Usage, pls. refer to the file usage in the wiki.
    instrumentJar = ‘The path of instrument.jar which generated in host project’
    acesoMapping = ‘The path of aceso-mapping.txt which generated in host project’
}

dependencies {
	// refer to the file usage in the wiki and aceso-demo-fix project
    provided files(‘The path of all-classes.jar which generated in host project’)
}

```
 
4.Pls copy the class to be fixed to Fix project, and reserve the package name, e.g., the class to be fixed named  `com.mogujie.aceso.demo.MainActivity`, the MainActivity in fix project should reserve the package name as `com.mogujie.aceso.demo.MainActivity`.

5.Fix you bug in Fix project.

6.Pls. add `@FixMtd` above the method to be fixed once methodLevelFix is set.

7.Execute command `gradle acesoRelease(or acesoDebug)` to generate the patch package, the patch would be generated under directory build/outputs/apk

8.Deploy the patch to the target phones.

## Demo
1.Compile and install aceso-demo, press the test button, the 'not fix' would be displayed

2.Execute command `gradle acesoRelease(or acesoDebug)` in project 'aceso-demo-fix'

3.Use `adb push` command to push the apk generated to path '/sdcard/fix.apk'

4.Press fix button on the phone

5.Press test button, the 'has been fixed' would be displayed!

 
## Thanks
- [Instant Run](https://developer.android.com/studio/run/index.html#instant-run)
- [Robust](http://tech.meituan.com/android_robust.html)


## License

Aceso is under the [Apache 2.0](LICENSE) license.
