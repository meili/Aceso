## Aceso

Aceso是基于Instant Run Hot Swap的Android热修复方案，使用它你能在不用重新发布版本的情况下对线上app的bug进行修复。

## Features

- 支持4.x-7.x机型
- 相比业界其他方案，基本没有兼容性问题
- 安装补丁后不用重启，实时生效
- 支持方法级别的修复
- 支持新增类


## Limitations

- 暂不支持static函数、构造函数的修复 
- 被proguard shrink或inline掉的函数不能修复
- 只支持修改函数体



## Usage
1.在最外层project的build.gradle中加入以下代码：

```
buildscript {
    repositories {
        jcenter()
    }

    dependencies {
         classpath 'com.mogujie.aceso:aceso-build:0.0.1'
    }
}
```

2.在module的build.gradle中加入以下代码：

```
apply plugin: 'Aceso'

dependencies {
    compile 'com.mogujie.aceso:aceso-android-lib:0.0.1'
}

```

如果你要在debug版本中使用aceso,需要再加入如下代码：

```
Aceso {
    instrumentDebug = false
}
```

3.在合适的位置加入如下代码：

```
 new Aceso().installPatch(optDir, patchFile);
```

4.每次**发布版本**时需要将module目录下的build/intermediates/aceso文件夹保存下来
 

## Generate Patch
1.你需要创建一个额外Fix的工程（可参考[aceso-demo-fix](aceso-demo-fix)）

2.在最外层project的build.gradle中加入以下代码：

```
buildscript {
    repositories {
        jcenter()
    }

    dependencies {
         classpath 'com.mogujie.aceso:aceso-build:0.0.1'
    }
}
```

3.在module的build.gradle中加入以下代码：

```
apply plugin: 'AcesoFix'

Aceso {
    //methodLevelFix为true时，是方法级的fix，也就是只对特定的方法进行修复，需要在修的方法前加@FixMtd的注解。否则是对整个类的所有方法进行修复。
    methodLevelFix = true
    //下面文件都在Usage第4步提到的aceso文件夹中，各个文件的作用请参考wiki
    instrumentJar = ‘宿主工程生成的instrument.jar的路径’
    allClassesJar = ‘宿主工程生成的all-classes.jar的路径’
    acesoMapping = ‘宿主工程生成的aceso-mapping.txt的路径’
}

```
 

4.将需要修改的类拷贝到Fix工程，并且保证包名不变。比如你需要修改的类为com.mogujie.aceso.demo.MainActivity，则你需要保证在fix工程中MainActivity的全限定名也为com.mogujie.aceso.demo.MainActivity。

5.进行你需要的修改

6.如果你将methodLevelFix设置为true，则需要对你修改的方法前加入@FixMtd的注解(com.android.annotations.FixMtd)

7.执行gradle/.gradlew acesoRelease(或acesoDebug)命令生成对应的补丁包。补丁包在/build/outputs/apk目录下

8.将补丁包下发到手机。


## Demo
1.编译并安装aceso-demo，点击test按钮，显示的是not fix! 

2.在aceso-demo-fix中执行gradle/.gradlew acesoRelease(或acesoDebug)

3.将产生的apk包push到手机端的/sdcard/fix.apk

4.在手机上点击fix按钮

5.点击test按钮，显示的是has been fixed !

 
## Thanks
- [Instant Run](https://developer.android.com/studio/run/index.html#instant-run)
- [Robust](http://tech.meituan.com/android_robust.html)


## License

Aceso is under the [Apache 2.0](LICENSE) license.
