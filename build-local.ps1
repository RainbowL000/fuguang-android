param([string]$Toolchain='D:\CodexAndroidToolchain', [string]$Jdk='D:\utilities\java\windows-x64\zulu-jdk17.0.7')
$ErrorActionPreference='Stop'
$env:JAVA_HOME=$Jdk
$env:ANDROID_HOME=Join-Path $Toolchain 'sdk'
$env:ANDROID_USER_HOME=Join-Path $Toolchain 'android-user'
$env:GRADLE_USER_HOME=Join-Path $Toolchain 'gradle-cache'
$env:FUGUANG_KEYSTORE=Join-Path $Toolchain 'fuguang-debug.keystore'
$env:TEMP=Join-Path $Toolchain 'temp'
$env:TMP=$env:TEMP
if (!(Test-Path -LiteralPath "$Jdk\bin\java.exe")) { throw 'Java 17 not found. Supply -Jdk.' }
if (!(Test-Path -LiteralPath "$Toolchain\gradle-8.9\bin\gradle.bat")) { throw 'Gradle 8.9 not found. Supply -Toolchain.' }
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null
if (!(Test-Path -LiteralPath $env:FUGUANG_KEYSTORE)) {
    & "$Jdk\bin\keytool.exe" -genkeypair -keystore $env:FUGUANG_KEYSTORE -storepass android -keypass android -alias androiddebugkey -dname 'CN=Fuguang Debug,O=Personal,C=CN' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Debug key generation failed.' }
}
Push-Location $PSScriptRoot
try {
    & "$Toolchain\gradle-8.9\bin\gradle.bat" --no-daemon --console=plain testDebugUnitTest assembleDebug lintDebug
    if ($LASTEXITCODE -ne 0) { throw 'Build or checks failed.' }
} finally { Pop-Location }
