@echo off
REM ==========================================
REM Multi-Agent Restaurant Simulation System
REM Dynamic Batch File - Works from any location
REM ==========================================

REM Get the directory where this batch file is located
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

REM Set project directories (relative to script location)
set "SRC_DIR=%SCRIPT_DIR%src"
set "BIN_DIR=%SCRIPT_DIR%bin"
set "LIB_DIR=%SCRIPT_DIR%lib"
set "MAIN_CLASS=mas.main.Main"

echo ==========================================
echo Multi-Agent Restaurant Simulation System
echo ==========================================
echo.
echo Script location: %SCRIPT_DIR%
echo Source directory: %SRC_DIR%
echo Binary directory: %BIN_DIR%
echo Library directory: %LIB_DIR%
echo.

REM Check if source directory exists
if not exist "%SRC_DIR%" (
    echo ERROR: Source directory not found: %SRC_DIR%
    pause
    exit /b 1
)

REM Check if JADE library exists
if not exist "%LIB_DIR%\jade.jar" (
    echo ERROR: JADE library not found: %LIB_DIR%\jade.jar
    pause
    exit /b 1
)

REM Create bin directory if it doesn't exist
if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"

echo [1/3] Cleaning previous compilation...
if exist "%BIN_DIR%\mas" rmdir /s /q "%BIN_DIR%\mas"
echo.

echo [2/3] Compiling Java sources...
echo Compiling from %SRC_DIR% to %BIN_DIR%...

REM Build classpath with absolute paths
set "CLASSPATH=%LIB_DIR%\jade.jar"

REM Compile all Java files with dynamic paths (no module-info.java)
javac -d "%BIN_DIR%" -cp "%CLASSPATH%" -encoding UTF-8 -sourcepath "%SRC_DIR%" ^
    "%SRC_DIR%\mas\core\BaseAgent.java" ^
    "%SRC_DIR%\mas\core\QueueManager.java" ^
    "%SRC_DIR%\mas\core\TickSystem.java" ^
    "%SRC_DIR%\mas\core\GridEnvironment.java" ^
    "%SRC_DIR%\mas\core\AStarPathfinding.java" ^
    "%SRC_DIR%\mas\core\AgentStatus.java" ^
    "%SRC_DIR%\mas\core\TickDuration.java" ^
    "%SRC_DIR%\mas\core\Menu.java" ^
    "%SRC_DIR%\mas\core\DebugLogger.java" ^
    "%SRC_DIR%\mas\agents\AgentFactoryAgent.java" ^
    "%SRC_DIR%\mas\agents\BossAgent.java" ^
    "%SRC_DIR%\mas\agents\CashierAgent.java" ^
    "%SRC_DIR%\mas\agents\ChefAgent.java" ^
    "%SRC_DIR%\mas\agents\ClientAgent.java" ^
    "%SRC_DIR%\mas\agents\EnterAgent.java" ^
    "%SRC_DIR%\mas\agents\ExitAgent.java" ^
    "%SRC_DIR%\mas\agents\TableAgent.java" ^
    "%SRC_DIR%\mas\agents\WaiterAgent.java" ^
    "%SRC_DIR%\mas\agents\HelperAgent.java" ^
    "%SRC_DIR%\mas\main\Main.java" ^
    "%SRC_DIR%\mas\main\ExampleUsage.java" 2>"%SCRIPT_DIR%compile_errors.txt"

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Compilation failed!
    echo Check compile_errors.txt for details.
    if exist "%SCRIPT_DIR%compile_errors.txt" (
        type "%SCRIPT_DIR%compile_errors.txt"
    )
    pause
    exit /b 1
)

echo Compilation successful!
echo.

echo [3/3] Starting JADE Platform with GUI...
echo.
echo Starting Main class: %MAIN_CLASS%
echo JADE GUI will open automatically...
echo.
echo ==========================================
echo.

REM Run the Main class with JADE library using absolute paths
java -cp "%BIN_DIR%;%LIB_DIR%\jade.jar" %MAIN_CLASS%

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Failed to start the application!
    pause
    exit /b 1
)

pause
    