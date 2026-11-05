<#
.SYNOPSIS
    KittyTune Wear OS Companion — E2E Test Suite Runner (PowerShell)
.DESCRIPTION
    Wraps the unified E2E test runner script and executes Tiers 1-4.
#>

[CmdletBinding()]
param()

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RunnerScript = Join-Path $ScriptDir "run_e2e_tests.py"

python $RunnerScript
exit $LASTEXITCODE
