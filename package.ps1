$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
if (Test-Path -LiteralPath (Join-Path $taskRoot 'local-env.ps1')) {
    . (Join-Path $taskRoot 'local-env.ps1')
}
$env:TEMP = Join-Path $taskRoot 'tmp'
$env:TMP = $env:TEMP
$env:JAVA_TOOL_OPTIONS = "-XX:-UsePerfData -Djava.io.tmpdir=$env:TEMP -Duser.home=$taskRoot/build-cache"
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 21 installation.' }
$taskSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
if (-not $taskSdk) { throw 'Set ANDROID_HOME or ANDROID_SDK_ROOT to the Android SDK.' }
$taskJava = Join-Path $env:JAVA_HOME 'bin'
$taskTools = Join-Path $taskSdk 'build-tools/37.0.0'
$taskOut = Join-Path $taskRoot 'out'
$taskSigning = Join-Path $taskRoot 'build-cache\signing'
New-Item -ItemType Directory -Path $taskOut,$taskSigning -Force | Out-Null
$taskKey = Join-Path $taskSigning 'lenovo-pen-ui.p12'
if (-not (Test-Path -LiteralPath $taskKey)) {
    & "$taskJava\keytool.exe" -genkeypair -keystore $taskKey -storetype PKCS12 -alias lenovo-pen-ui -keyalg RSA -keysize 3072 -validity 10000 -storepass android -keypass android -dname 'CN=Lenovo Pen UI Module, OU=Alyssum'
    if ($LASTEXITCODE -ne 0) { throw 'Module key generation failed' }
}
$taskSource = Join-Path $taskRoot 'app\build\outputs\apk\release\app-release-unsigned.apk'
$taskUnsigned = Join-Path $taskOut 'LenovoPenUI-Vector-v0.2.0-unsigned.apk'
$taskSigned = Join-Path $taskOut 'LenovoPenUI-Vector-v0.2.0.apk'
Copy-Item -LiteralPath $taskSource -Destination $taskUnsigned
& "$taskJava\java.exe" -jar "$taskTools\lib\apksigner.jar" sign --ks $taskKey --ks-key-alias lenovo-pen-ui --ks-pass pass:android --key-pass pass:android --v4-signing-enabled false --out $taskSigned $taskUnsigned
if ($LASTEXITCODE -ne 0) { throw 'Module APK signing failed' }
& "$taskJava\java.exe" -jar "$taskTools\lib\apksigner.jar" verify --verbose --print-certs $taskSigned
if ($LASTEXITCODE -ne 0) { throw 'APK verification failed' }
& "$taskTools\zipalign.exe" -c -p 4 $taskSigned
if ($LASTEXITCODE -ne 0) { throw 'APK alignment verification failed' }
Get-FileHash -LiteralPath $taskUnsigned,$taskSigned -Algorithm SHA256 | Format-Table -AutoSize
