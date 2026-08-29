@echo off
rem ===================================================================
rem  Custo Certo - abre o sistema completo
rem
rem  Faz tudo em um passo: liga o MySQL se ele nao estiver no ar,
rem  espera o banco responder e abre a aplicacao JavaFX.
rem
rem  Basta dar duplo clique neste arquivo.
rem ===================================================================
title Custo Certo - inicializacao
cd /d "%~dp0"

echo.
echo   CUSTO CERTO - Projeto Integrador FATEC Indaiatuba
echo   -------------------------------------------------
echo.

rem ----- 1. O MySQL ja esta rodando? -----
netstat -an | findstr /c:":3306" | findstr /c:"LISTENING" >nul
if not errorlevel 1 goto banco_pronto

echo   [1/2] Iniciando o servidor MySQL...
start "Custo Certo - MySQL (NAO FECHE esta janela)" "C:\Users\gabic\MySQL\iniciar-mysql.bat"

:esperar
timeout /t 1 /nobreak >nul
netstat -an | findstr /c:":3306" | findstr /c:"LISTENING" >nul
if errorlevel 1 goto esperar
goto banco_ok

:banco_pronto
echo   [1/2] MySQL ja estava rodando.
goto abrir

:banco_ok
echo         MySQL respondendo na porta 3306.

:abrir
echo   [2/2] Abrindo a aplicacao...
echo.
echo   Contas para entrar:
echo     admin@custocerto.com.br    / admin123      (Administrador)
echo     denilson@letscoffee.com.br / gestor123     (Gestor)
echo     marina@letscoffee.com.br   / operador123   (Operador)
echo.

call mvnw.cmd -q javafx:run

echo.
echo   Aplicacao encerrada.
echo   O MySQL continua rodando na outra janela - feche-a para desligar o banco.
echo.
pause
