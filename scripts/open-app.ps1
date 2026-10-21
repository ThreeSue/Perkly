param(
  [Parameter(Mandatory = $true)]
  [ValidateSet('client','merchant','platform-admin','merchant-independent','verification')]
  [string]$App,
  [switch]$Run
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$appPath = Join-Path $projectRoot "apps\$App"
$cliPath = $env:HBUILDERX_CLI
if ([string]::IsNullOrWhiteSpace($cliPath)) {
  $cliPath = 'D:\Downloads\HBuilderX.5.26.2026091802\HBuilderX\cli.exe'
}
if (-not (Test-Path -LiteralPath $cliPath)) { throw '请将 HBUILDERX_CLI 环境变量设为 HBuilderX 的 cli.exe 完整路径。' }
& $cliPath project open --path $appPath
if ($Run) { & $cliPath launch web --project $App --ui true }
Write-Output "业务首页：$appPath\pages\business\home.uvue"
