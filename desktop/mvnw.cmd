@echo off
@rem ---------------------------------------------------------------------------
@rem  Maven Wrapper - Custo Certo Desktop
@rem
@rem  Baixa o Apache Maven na versao definida em .mvn\wrapper\maven-wrapper.properties
@rem  e executa o comando pedido. Uso: mvnw.cmd <objetivo>   (ex: mvnw.cmd javafx:run)
@rem ---------------------------------------------------------------------------
setlocal enabledelayedexpansion

set "BASE_DIR=%~dp0"
set "WRAPPER_DIR=%BASE_DIR%.mvn\wrapper"
set "PROPS=%WRAPPER_DIR%\maven-wrapper.properties"

if not exist "%PROPS%" (
    echo [mvnw] Arquivo nao encontrado: %PROPS%
    exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%a in ("%PROPS%") do (
    if "%%a"=="mavenVersion"    set "MAVEN_VERSION=%%b"
    if "%%a"=="distributionUrl" set "DIST_URL=%%b"
)

set "MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo [mvnw] Apache Maven %MAVEN_VERSION% nao encontrado. Baixando uma unica vez...
    set "ZIP=%WRAPPER_DIR%\maven.zip"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "$ProgressPreference='SilentlyContinue';" ^
        "Invoke-WebRequest -Uri '!DIST_URL!' -OutFile '!ZIP!';" ^
        "Expand-Archive -Path '!ZIP!' -DestinationPath '%WRAPPER_DIR%' -Force;" ^
        "Remove-Item '!ZIP!' -Force"
    if errorlevel 1 (
        echo [mvnw] Falha ao baixar o Maven. Verifique a conexao com a internet.
        exit /b 1
    )
    echo [mvnw] Maven %MAVEN_VERSION% instalado em %MAVEN_HOME%
)

call "%MAVEN_HOME%\bin\mvn.cmd" %*
