param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$VersionName,

    [Parameter(Position = 1)]
    [int]$VersionCode = 0
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

if ($VersionName -notmatch '^\d+\.\d+\.\d+$') {
    throw "Version must look like 1.2.3. Example: .\release.ps1 1.1.0"
}

$buildFile = Join-Path $Root "app\build.gradle.kts"
$versionFile = Join-Path $Root "version.json"
$backupDir = Join-Path ([System.IO.Path]::GetTempPath()) ("brightness-release-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $backupDir | Out-Null
Copy-Item $buildFile (Join-Path $backupDir "build.gradle.kts")
Copy-Item $versionFile (Join-Path $backupDir "version.json")
$success = $false

function Get-JavaVersionText([string]$JavaPath) {
    $previousErrorAction = $ErrorActionPreference
    try {
        # java -version writes normal version text to stderr on Windows.
        $ErrorActionPreference = "Continue"
        return (& $JavaPath -version 2>&1 | Out-String)
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
}

try {
    # Prefer a compatible JDK 17 from the user's .jdks directory without changing
    # the system-wide Java installation.
    $javaCandidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $javaCandidates.Add((Join-Path $env:JAVA_HOME "bin\java.exe"))
    }
    $jdksRoot = Join-Path $env:USERPROFILE ".jdks"
    if (Test-Path $jdksRoot) {
        Get-ChildItem $jdksRoot -Recurse -Filter "java.exe" -File -ErrorAction SilentlyContinue |
            ForEach-Object { $javaCandidates.Add($_.FullName) }
    }

    $selectedJava = $null
    foreach ($candidate in $javaCandidates) {
        if (-not (Test-Path $candidate)) { continue }
        $candidateVersion = Get-JavaVersionText $candidate
        if ($candidateVersion -match 'version "17(?:\.|$)') {
            $selectedJava = $candidate
            break
        }
    }
    if ($selectedJava) {
        $javaHome = Split-Path (Split-Path $selectedJava -Parent) -Parent
        $env:JAVA_HOME = $javaHome
        $env:Path = "$javaHome\bin;$env:Path"
        Write-Host "Using project JDK: $javaHome"
    }

    $buildText = Get-Content $buildFile -Raw
    $currentMatch = [regex]::Match($buildText, 'versionCode\s*=\s*(\d+)')
    if (-not $currentMatch.Success) { throw "Could not find versionCode in app\build.gradle.kts" }
    if ($VersionCode -eq 0) { $VersionCode = [int]$currentMatch.Groups[1].Value + 1 }
    if ($VersionCode -lt 1) { throw "versionCode must be a positive integer" }

    $buildText = $buildText -replace 'versionCode\s*=\s*\d+', "versionCode = $VersionCode"
    $buildText = $buildText -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$VersionName`""
    Set-Content -Path $buildFile -Value $buildText -Encoding UTF8

    $versionJson = [ordered]@{
        versionCode = $VersionCode
        versionName = $VersionName
        notes = "See the GitHub release notes."
    } | ConvertTo-Json
    Set-Content -Path $versionFile -Value $versionJson -Encoding UTF8

    $javaVersion = Get-JavaVersionText "java.exe"
    if ($javaVersion -match 'version "(\d+)') {
        $javaMajor = [int]$Matches[1]
        if ($javaMajor -lt 17) {
            throw "JDK 17 or newer is required. Current Java: $javaMajor"
        }
        if ($javaMajor -gt 22) {
            throw "This project uses Gradle 8.9 and AGP 8.7.3. A compatible JDK 17 was not found in $jdksRoot. Current Java: $javaMajor"
        }
    }

    # Gradle needs the Android SDK location before it can configure lint and release tasks.
    $sdkRoot = $env:ANDROID_SDK_ROOT
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = $env:ANDROID_HOME }
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
    if (-not (Test-Path $sdkRoot)) {
        throw "Android SDK was not found at '$sdkRoot'. Set ANDROID_SDK_ROOT or install the SDK through Android Studio SDK Manager."
    }
    $sdkRoot = (Resolve-Path $sdkRoot).Path
    $env:ANDROID_SDK_ROOT = $sdkRoot
    $env:ANDROID_HOME = $sdkRoot

    # local.properties is machine-specific and is ignored by Git.
    $localProperties = Join-Path $Root "local.properties"
    $sdkForGradle = $sdkRoot -replace '\\', '/'
    $localLines = @()
    if (Test-Path $localProperties) {
        $localLines = @(Get-Content $localProperties)
    }
    $sdkLineWritten = $false
    $localLines = @($localLines | ForEach-Object {
        if ($_ -match '^\s*sdk\.dir\s*=') {
            $sdkLineWritten = $true
            "sdk.dir=$sdkForGradle"
        } else {
            $_
        }
    })
    if (-not $sdkLineWritten) { $localLines += "sdk.dir=$sdkForGradle" }
    Set-Content -Path $localProperties -Value $localLines -Encoding UTF8
    Write-Host "Using Android SDK: $sdkRoot"

    $gradle = Join-Path $Root "gradlew.bat"
    if (-not (Test-Path $gradle)) { throw "gradlew.bat was not found" }
    & $gradle clean assembleRelease
    $gradleExitCode = $LASTEXITCODE
    if ($gradleExitCode -ne 0) { throw "Gradle release build failed with exit code $gradleExitCode" }

    $releaseApk = Join-Path $Root "app\build\outputs\apk\release\app-release.apk"
    if (-not (Test-Path $releaseApk)) { throw "Release APK was not produced: $releaseApk" }
    $apkDir = Join-Path $Root "apk"
    New-Item -ItemType Directory -Path $apkDir -Force | Out-Null
    $publishedApk = Join-Path $apkDir "BrightnessControl.apk"
    Copy-Item $releaseApk $publishedApk -Force

    $sdkRoot = $env:ANDROID_SDK_ROOT
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = $env:ANDROID_HOME }
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
    $buildTools = Join-Path $sdkRoot "build-tools"
    $apksigner = Get-ChildItem $buildTools -Filter "apksigner.bat" -Recurse -File -ErrorAction SilentlyContinue |
        Sort-Object FullName | Select-Object -Last 1
    if (-not $apksigner) {
        $apksigner = Get-ChildItem $buildTools -Filter "apksigner" -Recurse -File -ErrorAction SilentlyContinue |
            Sort-Object FullName | Select-Object -Last 1
    }
    if ($apksigner) {
        & $apksigner.FullName verify --verbose $publishedApk
        if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed" }
    } else {
        Write-Warning "apksigner was not found; APK signature was not checked."
    }

    $hash = (Get-FileHash $publishedApk -Algorithm SHA256).Hash
    Write-Host "SHA-256: $hash"
    $success = $true
    Write-Host "Release $VersionName ($VersionCode) is ready in apk\BrightnessControl.apk"
    Write-Host "Commit apk\BrightnessControl.apk and version.json to the configured GitHub repository."
}
finally {
    if (-not $success) {
        Copy-Item (Join-Path $backupDir "build.gradle.kts") $buildFile -Force
        Copy-Item (Join-Path $backupDir "version.json") $versionFile -Force
        Write-Warning "Release preparation failed; version files were restored."
    }
    Remove-Item $backupDir -Recurse -Force -ErrorAction SilentlyContinue
}
