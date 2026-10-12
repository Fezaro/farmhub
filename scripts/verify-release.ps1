param(
    [string]$SdkRoot = (Join-Path $env:LOCALAPPDATA 'Android/Sdk'),
    [string]$BuildToolsVersion = '36.1.0',
    [string]$ExpectedVersionName = '1.0.12',
    [int]$ExpectedVersionCode = 13
)
$ErrorActionPreference = 'Stop'
$rolloutRoot = Split-Path $PSScriptRoot -Parent
$rolloutApk = Join-Path $rolloutRoot 'app/build/outputs/apk/release/app-release.apk'
$rolloutBundle = Join-Path $rolloutRoot 'app/build/outputs/bundle/release/app-release.aab'
$rolloutBuildTools = Join-Path $SdkRoot "build-tools/$BuildToolsVersion"
$rolloutBadging = & (Join-Path $rolloutBuildTools 'aapt.exe') dump badging $rolloutApk
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect release APK' }
$rolloutPackage = $rolloutBadging | Where-Object { $_ -like 'package:*' }
if ($rolloutPackage -notlike "*name='com.farm_tech.farmhub' versionCode='$ExpectedVersionCode' versionName='$ExpectedVersionName'*") {
    throw "Unexpected release identity: $rolloutPackage"
}
if ($rolloutBadging -contains 'application-debuggable') { throw 'Release must not be debuggable' }
$rolloutSignature = & (Join-Path $rolloutBuildTools 'apksigner.bat') verify --verbose --print-certs $rolloutApk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
$rolloutSigner = $rolloutSignature | Where-Object { $_ -like 'Signer #1 certificate SHA-256 digest:*' }
# Public signing identity verified against the published-baseline source rebuild.
if ($rolloutSigner -notlike '*0ad4c08faf14d9a59d3cef657ac5f5fd137db46316529eb5ef47dd13789933ae') {
    throw 'APK signing identity differs from the validated baseline'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($rolloutArtifact in @($rolloutApk, $rolloutBundle)) {
    $rolloutZip = [System.IO.Compression.ZipFile]::OpenRead($rolloutArtifact)
    try {
        if ($rolloutZip.Entries.FullName -match '(?i)rollout_ca|rollout_network_security|\.staging') {
            throw "Staging resource found in $rolloutArtifact"
        }
    } finally { $rolloutZip.Dispose() }
}
$rolloutBuildConfig = Get-Content (Join-Path $rolloutRoot 'app/build/generated/source/buildConfig/release/com/farm_tech/farmhub/BuildConfig.java') -Raw
if ($rolloutBuildConfig -notmatch 'API_BASE_URL = "https://api\.farmers-hub\.co\.ke/"' -or
    $rolloutBuildConfig -notmatch 'DEBUG = false') { throw 'Unexpected release endpoint/configuration' }
$rolloutMapping = Join-Path $rolloutRoot 'app/build/outputs/mapping/release/mapping.txt'
if (-not (Test-Path -LiteralPath $rolloutMapping) -or (Get-Item -LiteralPath $rolloutMapping).Length -eq 0) {
    throw 'R8 mapping file is missing; release obfuscation did not run'
}
$rolloutObfuscatedClasses = @(
    Select-String -Path $rolloutMapping -Pattern '^com\.farm_tech\.farmhub\..+ -> (?!com\.farm_tech\.farmhub\.)[^:]+:$'
)
if ($rolloutObfuscatedClasses.Count -eq 0) {
    throw 'Release mapping has no obfuscated FarmHub classes'
}
$rolloutBaseline = Join-Path $rolloutRoot 'artifacts/rollout/published-baseline/app/build/outputs/apk/release/app-release.apk'
if (Test-Path -LiteralPath $rolloutBaseline) {
    $rolloutBaselineBadging = & (Join-Path $rolloutBuildTools 'aapt.exe') dump badging $rolloutBaseline
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect baseline permissions' }
    $rolloutOldPermissions = @($rolloutBaselineBadging | Where-Object { $_ -like 'uses-permission*' })
    $rolloutPermissions = @($rolloutBadging | Where-Object { $_ -like 'uses-permission*' })
    if (Compare-Object $rolloutOldPermissions $rolloutPermissions) { throw 'Release permissions differ from baseline; review before upload' }
}
[pscustomobject]@{
    Package = 'com.farm_tech.farmhub'
    VersionName = $ExpectedVersionName
    VersionCode = $ExpectedVersionCode
    ApkSha256 = (Get-FileHash -LiteralPath $rolloutApk -Algorithm SHA256).Hash
    AabSha256 = (Get-FileHash -LiteralPath $rolloutBundle -Algorithm SHA256).Hash
    SigningIdentity = $rolloutSigner
    ProductionEndpoint = 'https://api.farmers-hub.co.ke/'
    StagingResources = 'Absent'
    ObfuscatedFarmHubClassMappings = $rolloutObfuscatedClasses.Count
} | ConvertTo-Json
