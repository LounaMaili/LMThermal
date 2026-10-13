<# Focused native preflight regressions: reject wrong SHA/context/dirty sources.
   No compiler, camera or canonical data is touched by these negative probes. #>
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$ReportDirectory)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$Root=Split-Path $PSScriptRoot -Parent
$Script=Join-Path $PSScriptRoot 'validate_lmtr_r3_windows.ps1'
$Actual=[string]::Join('', @(& git -C $Root rev-parse HEAD)).Trim()
if ($LASTEXITCODE -ne 0 -or $env:GITHUB_ACTIONS -ne 'true' -or $Actual -ne $env:GITHUB_SHA) {
    throw 'Focused guards require the actual authorized Actions SHA.'
}
$CurrentBranch=[string]::Join('', @(& git -C $Root branch --show-current)).Trim()
if ($CurrentBranch -ne '') { throw 'This regression targets an exact detached Actions checkout.' }
New-Item -ItemType Directory -Path $ReportDirectory | Out-Null
$Results=[Collections.Generic.List[object]]::new()
# Match the intended guard, so an unrelated exception cannot make a negative test pass.
function Expect-Rejection([string]$Name,[string]$ExpectedMessage,[string]$ExpectedSha) {
    $Path=Join-Path $ReportDirectory $Name
    $Rejected=$false
    try { & $Script -ExpectedCommit $ExpectedSha -ReportDirectory $Path }
    catch {
        if ($_.Exception.Message -notlike $ExpectedMessage) { throw }
        $Rejected=$true
    }
    if (-not $Rejected -or -not (Test-Path (Join-Path $Path 'failure.json'))) { throw "Guard failed: $Name" }
    $Results.Add(@{test=$Name;status='passed';expected=$ExpectedMessage})
}
Expect-Rejection 'wrong-head' 'Expected *; actual *' ('0'*40)
$OriginalRef=$env:GITHUB_REF
try {
    $env:GITHUB_REF='refs/heads/not-the-r3-branch'
    Expect-Rejection 'wrong-detached-ref' 'Detached HEAD requires *' $Actual
} finally { $env:GITHUB_REF=$OriginalRef }
$OriginalSha=$env:GITHUB_SHA
try {
    $env:GITHUB_SHA='0'*40
    Expect-Rejection 'wrong-detached-context-sha' 'Detached HEAD requires *' $Actual
} finally { $env:GITHUB_SHA=$OriginalSha }
$OriginalEvent=$env:GITHUB_EVENT_NAME
try {
    $env:GITHUB_EVENT_NAME='pull_request_target'
    Expect-Rejection 'wrong-event' 'Detached HEAD requires *' $Actual
} finally { $env:GITHUB_EVENT_NAME=$OriginalEvent }
# Only this uniquely owned sentinel is removed; no existing file is overwritten/discarded.
$Sentinel=Join-Path $Root ('lmtr-r3-guard-' + [guid]::NewGuid().ToString('N') + '.tmp')
try {
    [IO.File]::WriteAllText($Sentinel,'owned dirty-source refusal probe')
    Expect-Rejection 'dirty-source' 'Working tree must be clean *' $Actual
} finally { if (Test-Path $Sentinel) { Remove-Item -LiteralPath $Sentinel } }
if (@(& git -C $Root status --porcelain).Count -ne 0) { throw 'Guard probes left source changes.' }
@{status='passed';sha=$Actual;tests=$Results} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $ReportDirectory 'guards.json') -Encoding utf8
Write-Host 'Five strict detached/SHA/clean-source regression guards passed.'
