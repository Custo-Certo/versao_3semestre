@echo off
rem ===================================================================
rem  Custo Certo - gera o executavel do app desktop para Windows
rem
rem  Resultado, dentro de desktop\target\executavel\:
rem    Custo Certo\Custo Certo.exe  -> o programa, ja com o Java embutido
rem    Custo-Certo-Windows.zip      -> a mesma pasta compactada, para
rem                                    publicar em Releases no GitHub
rem
rem  Precisa so de um JDK 21 ou mais novo (o jpackage vem junto com ele).
rem  Basta dar duplo clique neste arquivo.
rem ===================================================================
title Custo Certo - gerando o executavel
setlocal
cd /d "%~dp0"

set "SAIDA=target\executavel"
set "ENTRADA=target\jpackage-entrada"
set "NOME=Custo Certo"

rem ----- 1. Achar o jpackage -----
set "JPACKAGE="
for /f "delims=" %%i in ('where jpackage 2^>nul') do if not defined JPACKAGE set "JPACKAGE=%%i"
if not defined JPACKAGE if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
if not defined JPACKAGE for /d %%d in ("C:\Program Files\Java\jdk-*" "C:\Program Files\Eclipse Adoptium\jdk-*" "C:\Program Files\Microsoft\jdk-*") do if exist "%%~d\bin\jpackage.exe" set "JPACKAGE=%%~d\bin\jpackage.exe"
if not defined JPACKAGE (
    echo   [ERRO] jpackage nao encontrado. Instale um JDK 21 ou mais novo.
    goto fim
)
echo   jpackage: %JPACKAGE%

rem ----- 2. O app nao pode estar aberto: o Windows trava os arquivos -----
tasklist /fi "imagename eq %NOME%.exe" 2>nul | find /i "%NOME%.exe" >nul
if not errorlevel 1 (
    echo   [ERRO] O Custo Certo esta aberto. Feche todas as janelas dele e rode de novo.
    goto fim
)

rem ----- 3. Compilar e juntar as bibliotecas -----
echo   [1/3] Compilando o app...
if exist "%ENTRADA%" rmdir /s /q "%ENTRADA%"
call "%~dp0mvnw.cmd" -q package dependency:copy-dependencies -DoutputDirectory=%ENTRADA% -DincludeScope=runtime -DskipTests
if errorlevel 1 (
    echo   [ERRO] A compilacao falhou.
    goto fim
)
copy /y target\custo-certo-desktop-1.0.0.jar "%ENTRADA%\" >nul

rem ----- 4. Montar o executavel -----
echo   [2/3] Montando o executavel...
if exist "%SAIDA%" rmdir /s /q "%SAIDA%"
"%JPACKAGE%" --type app-image --name "%NOME%" --app-version 1.0.0 ^
    --vendor "Equipe Custo Certo - FATEC Indaiatuba" ^
    --input "%ENTRADA%" --main-jar custo-certo-desktop-1.0.0.jar ^
    --main-class br.com.custocerto.Launcher ^
    --icon empacotamento\custo-certo.ico ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --dest "%SAIDA%"
if errorlevel 1 (
    echo   [ERRO] O jpackage falhou.
    goto fim
)
copy /y config.properties.example "%SAIDA%\%NOME%\" >nul
copy /y empacotamento\LEIA-ME.txt "%SAIDA%\%NOME%\" >nul

rem ----- 5. Compactar, sem a senha de ninguem -----
echo   [3/3] Compactando...
powershell -NoProfile -Command "Compress-Archive -Path '%SAIDA%\%NOME%' -DestinationPath '%SAIDA%\Custo-Certo-Windows.zip' -Force"

rem A configuracao local entra so na copia desta maquina, depois do zip.
if exist config.properties copy /y config.properties "%SAIDA%\%NOME%\" >nul

echo.
echo   Pronto:
echo     %CD%\%SAIDA%\%NOME%\%NOME%.exe
echo     %CD%\%SAIDA%\Custo-Certo-Windows.zip

:fim
echo.
pause
