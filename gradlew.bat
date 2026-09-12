@echo off
setlocal
set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%

if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)

if exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  "%JAVA_EXE%" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)

echo gradle-wrapper.jar is missing. Install Gradle or open the project in Android Studio.
exit /b 1
