<# Camera-free native Windows R3 validation. Evidence is written outside the checkout.
   This script never pairs ADB, opens USB, repins fixtures or changes production code. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{40}$')][string]$ExpectedCommit,
    [string]$ReportDirectory = (Join-Path $env:TEMP ('lmtr-r3-' + [guid]::NewGuid().ToString('N'))),
    [string]$Python = 'python',
    [switch]$Offline,
    [switch]$NtfsSparse,
    [string]$SparseTempRoot = $env:TEMP
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ([Environment]::OSVersion.Platform -ne 'Win32NT') { throw 'Native Windows is required; WSL is not this gate.' }
$Root = Split-Path $PSScriptRoot -Parent
$Reports = [IO.Path]::GetFullPath($ReportDirectory)
if ($Reports.StartsWith($Root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Use a report directory outside the pinned checkout.'
}
New-Item -ItemType Directory -Path $Reports -ErrorAction Stop | Out-Null
Push-Location $Root
try {
    # Capture exit status before processing output; PowerShell does not throw for native failures.
    function Invoke-Checked([string]$Executable, [string[]]$Arguments, [string]$Log) {
        $Output = & $Executable @Arguments 2>&1
        $Code = $LASTEXITCODE
        if ($Log -eq 'java-version.txt') { $Output = $Output | Where-Object { $_ -match 'java.specification.version|java.runtime.version|java.vendor =|java.version =|openjdk version|Runtime Environment|Server VM' } }
        $Output | Out-File -LiteralPath (Join-Path $Reports $Log) -Encoding utf8
        if ($Code -ne 0) { throw "$Executable failed ($Code); see $Log" }
        $Output | Write-Host
    }
    $Head = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $Head -ne $ExpectedCommit) { throw "Expected $ExpectedCommit; actual $Head" }
    $Branch = (& git branch --show-current).Trim()
    if ($Branch -ne 'feat/lmtr-r3-contract-conformance') { throw "Unexpected branch: $Branch" }
    if (@(& git status --porcelain).Count -ne 0) { throw 'Working tree must be clean before validation.' }
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 17 installation.' }
    $Java = Join-Path $env:JAVA_HOME 'bin/java.exe'
    Invoke-Checked $Java @('-XshowSettings:properties','-version') 'java-version.txt'
    if (-not (Select-String -LiteralPath (Join-Path $Reports 'java-version.txt') -Pattern 'java.specification.version = 17$' -Quiet)) { throw 'JDK 17 is required.' }
    Invoke-Checked $Python @('-c','import sys,platform,zlib,json; print(json.dumps(dict(python=sys.version,platform=platform.platform(),zlib_runtime=zlib.ZLIB_RUNTIME_VERSION)))') 'python-version.txt'
    $env:PYTHONDONTWRITEBYTECODE = '1'
    $Manifest = Join-Path $Root 'lmtr-contract/src/test/resources/lmtr/manifest.json'
    $ManifestBefore = (Get-FileHash -LiteralPath $Manifest -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($ManifestBefore -ne '29b262a1f831b119c5094dd7385869ea03fdd44b0947263922ead0bd77f660e3') { throw 'Pinned corpus manifest differs.' }
    $GradleArgs = @('-p','tools/lmtr-r3-host',':lmtr-contract:clean',':lmtr-contract:test',':lmtr-contract:writeRuntimeClasspath',':lmtr-contract:writeTestRuntimeClasspath')
    if ($Offline) { $GradleArgs = @('--offline') + $GradleArgs }
    Invoke-Checked (Join-Path $Root 'gradlew.bat') $GradleArgs 'gradle.txt'
    # Discovery includes the focused combined-corruption and cancellation regressions.
    Invoke-Checked $Python @('-W','error','-m','unittest','discover','-s','tools/lmtr_v1/tests','-t','.','-v') 'python-tests.txt'
    Invoke-Checked $Python @('tools/check_lmtr_conformance.py','--report',(Join-Path $Reports 'interop.json')) 'interop.txt'
    Invoke-Checked $Python @('tools/check_lmtr_hardening.py','--report',(Join-Path $Reports 'hardening.json')) 'hardening.txt'
    Invoke-Checked $Python @('tools/check_lmtr_cancellation.py','--report',(Join-Path $Reports 'cancellation.json')) 'cancellation.txt'
    if ($NtfsSparse) {
        $Classpath = (Get-Content -LiteralPath 'lmtr-contract/build/runtime-classpath.txt' -Raw).Trim()
        Invoke-Checked $Python @('tools/check_lmtr_ntfs.py','--java',$Java,'--classpath',$Classpath,'--temp-root',$SparseTempRoot,'--full','--report',(Join-Path $Reports 'ntfs.json')) 'ntfs.txt'
    }
    Invoke-Checked 'git' @('diff','--check') 'diff-check.txt'
    if (@(& git status --porcelain).Count -ne 0) { throw 'Source checkout changed during validation.' }
    $ManifestAfter = (Get-FileHash -LiteralPath $Manifest -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($ManifestBefore -ne $ManifestAfter) { throw 'Pinned manifest changed.' }
    $Tests=0; $Failures=0; $Errors=0; $Skipped=0
    foreach ($File in Get-ChildItem 'lmtr-contract/build/test-results/test/TEST-*.xml') {
        [xml]$Xml = Get-Content -LiteralPath $File.FullName -Raw
        $Tests += [int]$Xml.testsuite.tests; $Failures += [int]$Xml.testsuite.failures
        $Errors += [int]$Xml.testsuite.errors; $Skipped += [int]$Xml.testsuite.skipped
    }
    if ($Tests -eq 0 -or $Failures -ne 0 -or $Errors -ne 0) { throw 'Missing/failing JVM result XML.' }
    # Per-command logs retain exact Python counts and independent outcomes even if a later gate fails.
    $Volume = Get-Volume -DriveLetter ([IO.Path]::GetPathRoot($Reports).Substring(0,1))
    @{ commit=$Head; branch=$Branch; windows=[Environment]::OSVersion.VersionString;
       report_filesystem=$Volume.FileSystem; manifest_sha256=$ManifestAfter;
       jvm_tests=$Tests; jvm_failures=$Failures; jvm_errors=$Errors; jvm_skipped=$Skipped;
       ntfs_sparse=$(if ($NtfsSparse) {'executed; see ntfs.json'} else {'NOT EXECUTED'});
       source_immutability='clean checkout and per-fixture SHA checks; manifest unchanged';
       status='passed requested native gates; operator must review retained reports';
       accuracy_warning='Native-equivalent temperatures; absolute physical accuracy not yet independently validated.'
    } | ConvertTo-Json -Depth 8 | Out-File -LiteralPath (Join-Path $Reports 'summary.json') -Encoding utf8
    Write-Host "Validation reports: $Reports"
} finally { Pop-Location }
