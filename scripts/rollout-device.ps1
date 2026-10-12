param(
    [ValidatePattern('^[a-z0-9-]+$')][string]$Name = 'device-check',
    [string]$Serial = 'emulator-5554',
    [switch]$Screenshot
)
$ErrorActionPreference = 'Stop'
$rolloutRoot = Split-Path $PSScriptRoot -Parent
$rolloutAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$rolloutEvidence = Join-Path $rolloutRoot 'artifacts/rollout'
New-Item -ItemType Directory -Force -Path $rolloutEvidence | Out-Null
$rolloutRemote = '/sdcard/rollout-' + [guid]::NewGuid().ToString('N')
$rolloutDumpOutput = & $rolloutAdb -s $Serial shell uiautomator dump "$rolloutRemote.xml" 2>&1
if ($LASTEXITCODE -ne 0 -or "$rolloutDumpOutput" -notmatch 'dumped to:') {
    throw "Device did not return a fresh UI snapshot: $rolloutDumpOutput"
}
$rolloutXmlPath = Join-Path $rolloutEvidence "$Name.xml"
& $rolloutAdb -s $Serial pull "$rolloutRemote.xml" $rolloutXmlPath | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot retrieve UI snapshot' }
[xml]$rolloutUi = Get-Content -LiteralPath $rolloutXmlPath
# Never print the contents of password fields.
$rolloutUi.SelectNodes('//node[(@text!="" or @content-desc!="") and @password!="true"]') |
    ForEach-Object { "$($_.text) $($_.'content-desc') $($_.bounds)" }
if ($Screenshot) {
    & $rolloutAdb -s $Serial shell screencap -p "$rolloutRemote.png"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot capture device screenshot' }
    & $rolloutAdb -s $Serial pull "$rolloutRemote.png" (Join-Path $rolloutEvidence "$Name.png") | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot retrieve device screenshot' }
}
# Delete only this capture's exact temporary device paths after retrieval, so
# diagnostic screenshots do not clutter the app's attachment picker.
& $rolloutAdb -s $Serial shell rm -f "$rolloutRemote.xml" "$rolloutRemote.png" | Out-Null
