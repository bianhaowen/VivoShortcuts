@echo off
set GRADLE_HOME=C:\Users\bianh\devtools\gradle-8.12
set JAVA_HOME=C:\Users\bianh\devtools\jdk-17.0.19+10

set CLASSPATH=
for %%f in ("%GRADLE_HOME%\lib\*.jar") do (
    if "!CLASSPATH!"=="" (set CLASSPATH=%%f) else (set CLASSPATH=!CLASSPATH!;%%f)
)

setlocal enabledelayedexpansion
set CLASSPATH=
for %%f in ("%GRADLE_HOME%\lib\*.jar") do (
    if "!CLASSPATH!"=="" (set "CLASSPATH=%%f") else (set "CLASSPATH=!CLASSPATH!;%%f")
)
"%JAVA_HOME%\bin\java.exe" -classpath "!CLASSPATH!" org.gradle.launcher.GradleMain %*
endlocal
