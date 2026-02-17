# What is it?

This is a fixed version of maflcko.Imker app.

## I'm not a programmer. How do I run it?

Download latest `.jar` file here: <https://github.com/Podbrushkin/Imker-hotfix/releases> under **Assets** and double-click it.

## It didn't work.

In the same directory with `.jar` file create a text file with single line in it:
```
java -jar imker.jar
```
Rename it to `ImkerRun.bat` (or any other name with `.bat` extension) and double-click this file to run Imker.

## It still didn't work.

Create an issue in this repository, explain what's wrong.
Also, there's a chance of getting get help here: <https://commons.wikimedia.org/wiki/Commons_talk:Imker_(batch_download)>

## What exactly did you change in Imker?

To `wiki/Wiki.java` file at line ~7261 I've added this catch clause to existing `try(){}` block:
```java
catch (ZipException ze) {
    zipped = false;
    return fetch(url, caller);
}
```

Also, I've moved project from Gradle to Maven build system. 
And explicitly specified UTF-8 for resource files to fix encoding problem.
