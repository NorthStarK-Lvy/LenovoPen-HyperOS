param([switch]$Online, [string[]]$Tasks = @(':app:assembleRelease', ':app:penPolicyTest', ':app:lintRelease'))
$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
if (Test-Path -LiteralPath (Join-Path $taskRoot 'local-env.ps1')) {
    . (Join-Path $taskRoot 'local-env.ps1')
}
$env:ANDROID_USER_HOME = Join-Path $taskRoot 'build-cache/android'
$env:GRADLE_USER_HOME = Join-Path $taskRoot 'build-cache/gradle'
$env:TEMP = Join-Path $taskRoot 'tmp'
$env:TMP = $env:TEMP
$env:JAVA_TOOL_OPTIONS = "-XX:-UsePerfData -Djava.io.tmpdir=$env:TEMP -Duser.home=$taskRoot/build-cache"
New-Item -ItemType Directory -Path $env:GRADLE_USER_HOME,$env:ANDROID_USER_HOME,$env:TEMP -Force | Out-Null
$taskGradle = if ($env:GRADLE_BIN) { $env:GRADLE_BIN } else { Join-Path $taskRoot 'gradlew.bat' }
$taskArguments = @('--no-daemon','--console=plain','-p',$taskRoot) + $Tasks
if (-not $Online) { $taskArguments = @('--offline') + $taskArguments }
& $taskGradle @taskArguments
if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
