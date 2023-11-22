# What is it?

This is a fixed version of maflcko.Imker app.

## I'm not a programmer. How do I run it?

Download `.jar` file here: <https://github.com/Podbrushkin/Imker-hotfix/releases> under **Assets** and double-click it.

Or use this direct link to download: <https://github.com/Podbrushkin/Imker-hotfix/releases/download/0.0.1/imker-hotfix-0.0.1-SNAPSHOT-jar-with-dependencies.jar>

## It didn't work.

In the same directory with `.jar` file create a text file with single line in it:
```
java -jar imker-hotfix-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```
Rename it to  `ImkerRun.bat` and double-click this file to run Imker.

## It still didn't work.

Explain what's wrong here: <https://commons.wikimedia.org/wiki/Commons_talk:Imker_(batch_download)>

## What exactly did you change in Imker?

I've moved project from Gradle to Maven build system. 

And to `wiki/Wiki.java` file at line ~7261 I've added this catch clause to existing `try(){}` block:
```java
catch (ZipException ze) {
    zipped = false;
    return fetch(url, caller);
}
```
