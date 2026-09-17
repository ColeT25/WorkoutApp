$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$agentPath = Join-Path $repoRoot "workout-agent\build\install\workout-agent\bin\workout-agent.bat"
if (-not (Test-Path -LiteralPath $agentPath)) {
    & (Join-Path $repoRoot "gradlew.bat") :workout-agent:installDist --no-daemon
}

$smokeDir = Join-Path ([System.IO.Path]::GetTempPath()) "WorkoutAppSmoke-$([Guid]::NewGuid().ToString())"
New-Item -ItemType Directory -Path $smokeDir | Out-Null
$databasePath = Join-Path $smokeDir "workout.db"
$process = $null

try {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $agentPath
    $startInfo.Arguments = "--jsonl --db `"$databasePath`""
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()

    function Send-Command([string]$command) {
        $process.StandardInput.WriteLine($command)
        $process.StandardInput.Flush()
        $line = $process.StandardOutput.ReadLine()
        if ([string]::IsNullOrWhiteSpace($line)) {
            throw "The agent returned no response"
        }
        $response = $line | ConvertFrom-Json
        if (-not $response.ok) {
            throw "Agent command failed: $($response.code) - $($response.error)"
        }
        return $response.result
    }

    $exercise = Send-Command '{"op":"create_exercise","name":"Smoke Bench","sets":3,"reps":5,"weightLb":100,"increaseLb":5,"decreaseLb":10}'
    $workout = Send-Command '{"op":"create_workout","name":"Smoke Push"}'
    [void](Send-Command "{`"op`":`"add_exercise`",`"workoutId`":`"$($workout.id)`",`"exerciseId`":`"$($exercise.id)`"}")
    $session = Send-Command "{`"op`":`"start_session`",`"workoutId`":`"$($workout.id)`",`"date`":`"2026-09-17`"}"
    [void](Send-Command "{`"op`":`"mark_exercise`",`"sessionId`":`"$($session.id)`",`"plannedExerciseId`":`"$($exercise.id)`",`"outcome`":`"COMPLETED`"}")
    $finished = Send-Command "{`"op`":`"finish_session`",`"sessionId`":`"$($session.id)`"}"
    if ($finished.status -ne "COMPLETED") {
        throw "Expected completed session, got $($finished.status)"
    }

    $state = Send-Command '{"op":"state"}'
    $persisted = $state.exercises | Where-Object { $_.id -eq $exercise.id }
    if ($null -eq $persisted -or $persisted.weightLb -ne 105) {
        throw "Expected progressive overload to set Smoke Bench to 105 lb"
    }

    $process.StandardInput.Close()
    $process.WaitForExit()
    Write-Output "Headless smoke passed: $($finished.id) completed and weight progressed to $($persisted.weightLb) lb."
}
finally {
    if ($null -ne $process -and -not $process.HasExited) {
        $process.Kill()
        $process.WaitForExit()
    }
    if (Test-Path -LiteralPath $smokeDir) {
        Remove-Item -LiteralPath $smokeDir -Recurse -Force
    }
}
