param(
    [string]$VersionName,
    [int]$VersionCode,
    [string]$CommitMessage = "",
    [string]$Branch = "",
    [switch]$RunTests,
    [switch]$Commit,
    [switch]$Push
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$buildFile = Join-Path $repoRoot "app/build.gradle.kts"
if (-not (Test-Path $buildFile)) {
    throw "Cannot find build file: $buildFile"
}

$javaHome = "C:\Program Files\Android\Android Studio\jbr"
if (-not (Test-Path $javaHome)) {
    throw "JAVA_HOME not found: $javaHome"
}

$env:JAVA_HOME = $javaHome
$env:Path = "$($env:JAVA_HOME)\bin;$($env:Path)"
$env:GRADLE_USER_HOME = Join-Path $repoRoot ".gradle-local"
$env:ANDROID_USER_HOME = Join-Path $repoRoot ".android-user-home-release"

$content = Get-Content -Raw -Path $buildFile

$currentVersionNameMatch = [regex]::Match($content, 'versionName = "([^"]+)"')
$currentVersionCodeMatch = [regex]::Match($content, 'versionCode = (\d+)')
if (-not $currentVersionNameMatch.Success -or -not $currentVersionCodeMatch.Success) {
    throw "Failed to parse versionName/versionCode from $buildFile"
}

$currentVersionName = $currentVersionNameMatch.Groups[1].Value
$currentVersionCode = [int]$currentVersionCodeMatch.Groups[1].Value

if (-not $VersionName) {
    $parts = $currentVersionName.Split(".")
    if ($parts.Count -lt 3) {
        throw "VersionName '$currentVersionName' is not patch-style. Pass -VersionName explicitly."
    }
    $parts[$parts.Count - 1] = ([int]$parts[$parts.Count - 1] + 1).ToString()
    $VersionName = $parts -join "."
}

if (-not $VersionCode) {
    $VersionCode = $currentVersionCode + 1
}

if ($VersionCode -lt $currentVersionCode) {
    throw "VersionCode must not be less than current value $currentVersionCode"
}

if ($VersionName -ne $currentVersionName -or $VersionCode -ne $currentVersionCode) {
    $updated = $content `
        -replace 'versionCode = \d+', "versionCode = $VersionCode" `
        -replace 'versionName = "[^"]+"', "versionName = `"$VersionName`""

    Set-Content -Path $buildFile -Value $updated -Encoding UTF8

    Write-Host "Updated versionName: $currentVersionName -> $VersionName"
    Write-Host "Updated versionCode: $currentVersionCode -> $VersionCode"
} else {
    Write-Host "Version already set to $VersionName ($VersionCode)"
}

if ($RunTests) {
    & ".\gradlew.bat" --no-daemon :app:testDebugUnitTest
    if ($LASTEXITCODE -ne 0) {
        throw "Unit tests failed"
    }
}

& ".\gradlew.bat" --no-daemon :app:assembleRelease
if ($LASTEXITCODE -ne 0) {
    throw "Release build failed"
}

$apkPath = Join-Path $repoRoot "app/build/outputs/apk/release/app-release.apk"
if (-not (Test-Path $apkPath)) {
    throw "Release APK not found: $apkPath"
}

Write-Host "Release APK: $apkPath"

if ($Commit) {
    if (-not $CommitMessage) {
        $CommitMessage = "chore: release $VersionName"
    }

    git add app/build.gradle.kts
    git add scripts/build-release.ps1
    git commit -m $CommitMessage
    if ($LASTEXITCODE -ne 0) {
        throw "Git commit failed"
    }

    if ($Push) {
        if (-not $Branch) {
            $Branch = (git branch --show-current).Trim()
        }
        if (-not $Branch) {
            throw "Cannot determine current branch. Pass -Branch explicitly."
        }
        git push origin $Branch
        if ($LASTEXITCODE -ne 0) {
            throw "Git push failed"
        }
    }
}
