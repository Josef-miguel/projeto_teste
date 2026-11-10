@echo off
rem Compila o projeto (requer JDK 21+)
cd /d "%~dp0"

set "JDK="
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-*") do set "JDK=%%D"
if "%JDK%"=="" (
  echo [ERRO] JDK nao encontrado em C:\Program Files\Eclipse Adoptium
  exit /b 1
)

echo Compilando com %JDK%...
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
"%JDK%\bin\javac.exe" -encoding UTF-8 -cp "lib\mysql-connector-j-9.4.0.jar" -d out @sources.txt
del sources.txt
if %errorlevel%==0 (
  echo Compilacao concluida com sucesso.
) else (
  echo [ERRO] Falha na compilacao.
  exit /b 1
)
