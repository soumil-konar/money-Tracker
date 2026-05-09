@ECHO OFF
SETLOCAL

SET DIRNAME=%~dp0
IF "%DIRNAME%"=="" SET DIRNAME=.
SET APP_HOME=%DIRNAME%
SET CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

IF DEFINED JAVA_HOME GOTO findJavaFromJavaHome

SET JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
IF "%ERRORLEVEL%" NEQ "0" (
  ECHO ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
  ECHO Please set JAVA_HOME to your JDK install directory, or use Android Studio's bundled JDK.
  EXIT /B 1
)
GOTO execute

:findJavaFromJavaHome
SET JAVA_HOME=%JAVA_HOME:"=%
SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
IF EXIST "%JAVA_EXE%" GOTO execute

ECHO ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
ECHO Please set JAVA_HOME to your JDK install directory, or use Android Studio's bundled JDK.
EXIT /B 1

:execute
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%~nx0" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

ENDLOCAL

