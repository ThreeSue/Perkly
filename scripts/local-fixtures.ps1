param(
    [ValidateSet('Inspect', 'Seed', 'Verify')]
    [string]$Mode = 'Verify',
    [string]$BackendPath = 'C:\Users\Eric\Perkly'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$archiveDirectory = Join-Path $projectRoot 'docs\fixtures\interop-20261021'
$javaCommand = Get-Command java -ErrorAction Stop
$driverJar = Join-Path $env:USERPROFILE '.m2\repository\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar'
$jsonJar = Join-Path $env:USERPROFILE '.m2\repository\cn\hutool\hutool-all\5.8.26\hutool-all-5.8.26.jar'
foreach ($dependency in @($driverJar, $jsonJar)) {
    if (-not (Test-Path -LiteralPath $dependency)) { throw "Missing local dependency: $dependency" }
}
$sourceFile = Join-Path $PSScriptRoot 'LocalSeedData.java'
$javaArguments = @('-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-cp', "$driverJar;$jsonJar", $sourceFile, $BackendPath, $archiveDirectory)
if ($Mode -ne 'Inspect') { $javaArguments += $Mode.ToLowerInvariant() }
& $javaCommand.Source @javaArguments
if ($LASTEXITCODE -ne 0) { throw "Fixture helper failed ($LASTEXITCODE). Check the batch manifest before retrying." }
