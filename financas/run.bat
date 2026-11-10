@echo off
rem Inicia o servidor web do sistema financeiro
cd /d "%~dp0"

if not exist out\com\financas\App.class (
  call build.bat
  if errorlevel 1 exit /b 1
)

set "JDK="
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-*") do set "JDK=%%D"

"%JDK%\bin\java.exe" -cp "out;lib\mysql-connector-j-9.4.0.jar" com.financas.App
