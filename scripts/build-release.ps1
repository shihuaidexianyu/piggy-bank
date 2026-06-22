param(
    [string]$VersionName,
    [int]$VersionCode,
    [string]$CommitMessage = "",
    [string]$Branch = "",
    [switch]$RunTests,
    [switch]$Commit,
    [switch]$Push,
    [switch]$Tag,
    [switch]$AllowDirty
)

$ErrorActionPreference = "Stop"

# Functions must be defined before they are called. PowerShell parses top-to-bottom.
function Resolve-ApksignerPath {
    param([string]$RepoRoot)
    # Try the local SDK path (set by gradle/local.properties or env).
    $sdkRoot = $env:ANDROID_SDK_ROOT
    if (-not $sdkRoot) {
        $localProps = Join-Path $RepoRoot "local.properties"
        if (Test-Path $localProps) {
            $match = Get-Content $localProps | Select-String -Pattern '^sdk\.dir=(.+)$'
            if ($match) { $sdkRoot = $match.Matches[0].Groups[1].Value.Trim() }
        }
    }
    if (-not $sdkRoot) { return $null }
    $buildToolsDir = Join-Path $sdkRoot "build-tools"
    if (-not (Test-Path $buildToolsDir)) { return $null }
    $latest = Get-ChildItem $buildToolsDir -Directory | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $latest) { return $null }
    $candidates = @(
        (Join-Path $latest.FullName "apksigner.bat"),
        (Join-Path $latest.FullName "apksigner")
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    return $null
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not $AllowDirty) {
    $gitStatus = git status --porcelain
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to check git working tree status."
    }
    if ($gitStatus) {
        throw "Working tree is not clean. Commit or stash changes before release, or pass -AllowDirty if this is intentional."
    }
}

$buildFile = Join-Path $repoRoot "app/build.gradle.kts"
if (-not (Test-Path $buildFile)) {
    throw "Cannot find build file: $buildFile"
}

$javaHome = "C:\Program Files\Android\Android Studio\jbr"
if (-not (Test-Path $javaHome)) {
    throw "JAVA_HOME not found at '$javaHome'. Install Android Studio with its bundled JBR or update `$javaHome in this script."
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
    Write-Host "Running unit tests..."
    & ".\gradlew.bat" --no-daemon :app:testDebugUnitTest
    if ($LASTEXITCODE -ne 0) {
        throw "Unit tests failed with exit code $LASTEXITCODE."
    }
}

Write-Host "Building release APK..."
& ".\gradlew.bat" --no-daemon :app:assembleRelease
if ($LASTEXITCODE -ne 0) {
    throw "Release build failed with exit code $LASTEXITCODE."
}

$apkPath = Join-Path $repoRoot "app/build/outputs/apk/release/app-release.apk"
if (-not (Test-Path $apkPath)) {
    throw "Release APK not found: $apkPath"
}

Write-Host "Release APK: $apkPath"

# Verify the APK is actually signed with the release key (not the debug fallback).
# `apksigner verify` exits 0 only when the APK's signature is valid; combined with
# `--print-certs` we can spot-check that it's our release cert, not the debug one.
$apksigner = Resolve-ApksignerPath -RepoRoot $repoRoot
if ($apksigner) {
    Write-Host "Verifying APK signature with $apksigner ..."
    & $apksigner verify --verbose --print-certs $apkPath
    if ($LASTEXITCODE -ne 0) {
        throw "APK signature verification failed. The APK may be signed with the debug key — check that signing/keystore.properties exists."
    }
    Write-Host "APK signature OK."
} else {
    Write-Warning "apksigner not found in SDK build-tools; skipping signature verification. Manually run: apksigner verify $apkPath"
}

if ($Commit) {
    if (-not $CommitMessage) {
        $CommitMessage = "chore: release $VersionName"
    }

    git add app/build.gradle.kts
    if ($LASTEXITCODE -ne 0) {
        throw "Git add failed for app/build.gradle.kts"
    }
    git add scripts/build-release.ps1
    if ($LASTEXITCODE -ne 0) {
        throw "Git add failed for scripts/build-release.ps1"
    }
    git commit -m $CommitMessage
    if ($LASTEXITCODE -ne 0) {
        throw "Git commit failed with exit code $LASTEXITCODE."
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
            throw "Git push failed with exit code $LASTEXITCODE."
        }
    }

    if ($Tag) {
        $tagName = "v$VersionName"
        git tag -a $tagName -m "Release $VersionName (versionCode $VersionCode)"
        if ($LASTEXITCODE -ne 0) {
            throw "Git tag creation failed for $tagName"
        }
        Write-Host "Created tag $tagName"
        if ($Push) {
            git push origin $tagName
            if ($LASTEXITCODE -ne 0) {
                throw "Git push tag failed for $tagName"
            }
        }
    }
}
