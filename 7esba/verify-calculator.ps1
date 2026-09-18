$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$tools = Join-Path $root '.tools'
$jdk = Join-Path $tools 'jdk'
$kotlinc = Join-Path $tools 'kotlinc\bin\kotlinc.bat'
$kotlinStdlib = Join-Path $tools 'kotlinc\lib\kotlin-stdlib.jar'
$out = Join-Path $root 'build\tests'

foreach ($tool in @("$jdk\bin\java.exe", $kotlinc, $kotlinStdlib)) {
    if (!(Test-Path $tool)) { throw "Missing tool: $tool -- run .tools\get-toolchain.ps1 first" }
}

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item $out -ItemType Directory -Force | Out-Null

Write-Output '==> compiling calculator logic tests'
& $kotlinc -nowarn -jvm-target 17 -d $out `
    (Join-Path $root 'core\src\main\kotlin') `
    (Join-Path $root 'core\src\test\kotlin')
if ($LASTEXITCODE -ne 0) { throw 'kotlinc failed' }

Write-Output '==> running checks'
& "$jdk\bin\java.exe" -cp "$out;$kotlinStdlib" com.hesba.core.CalculatorTestKt
if ($LASTEXITCODE -ne 0) { throw 'calculator checks failed' }