@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem ---------------------------------------------------------------------------
@rem Workaround: Windows NDK + spaces in project path
@rem
@rem ndk-build ultimately feeds paths into (GNU) make, which does not reliably
@rem handle spaces even when arguments are quoted. If the project path contains
@rem spaces, map it to a temporary drive letter with SUBST and run Gradle from
@rem there so all paths are space-free.
@rem ---------------------------------------------------------------------------
set "SUBST_DRIVE="
if not "%APP_HOME: =%"=="%APP_HOME%" (
  for %%L in (Z Y X W V U T) do call :trySubstDrive %%L
)

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem ---------------------------------------------------------------------------
@rem Find Java
@rem ---------------------------------------------------------------------------
set "JAVA_EXE="

@rem If JAVA_HOME is set but invalid, ignore it (common when pointing to a JRE/JDK stub).
if defined JAVA_HOME (
  set "JAVA_HOME=%JAVA_HOME:"=%"
  if "%JAVA_HOME:~-1%"=="\" set "JAVA_HOME=%JAVA_HOME:~0,-1%"
  if "%JAVA_HOME:~-1%"=="/" set "JAVA_HOME=%JAVA_HOME:~0,-1%"
  if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    goto execute
  ) else (
    echo WARNING: JAVA_HOME is set but "%JAVA_HOME%\bin\java.exe" was not found. Ignoring JAVA_HOME.
    set "JAVA_HOME="
  )
)

@rem Fallback: try common system JDK 21 locations (useful on fresh Windows installs).
for /d %%d in ("C:\Program Files\Microsoft\jdk-21.*-hotspot") do (
  if exist "%%d\bin\java.exe" (
    set "JAVA_EXE=%%d\bin\java.exe"
    goto execute
  )
)
for /d %%d in ("C:\Program Files\Java\jdk-21*") do (
  if exist "%%d\bin\java.exe" (
    set "JAVA_EXE=%%d\bin\java.exe"
    goto execute
  )
)

@rem Fallback: use java.exe from PATH
set "JAVA_EXE=java.exe"
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set (or invalid) and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
if defined SUBST_DRIVE (
  pushd "%SUBST_DRIVE%\" >NUL
)
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
set "GRADLE_EXIT_CODE=%ERRORLEVEL%"
if defined SUBST_DRIVE (
  popd >NUL
  subst %SUBST_DRIVE% /d >NUL 2>&1
)
if "%GRADLE_EXIT_CODE%"=="0" goto mainEnd
exit /b %GRADLE_EXIT_CODE%

@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega

:trySubstDrive
if defined SUBST_DRIVE exit /b 0
if exist "%~1:\NUL" exit /b 0
subst %~1: "%APP_HOME%" >NUL 2>&1
if not "%ERRORLEVEL%"=="0" exit /b 0
set "SUBST_DRIVE=%~1:"
set "APP_HOME=%~1:\"
set "DIRNAME=%APP_HOME%"
exit /b 0
