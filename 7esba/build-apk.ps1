param(
    [string]$PackageName = 'com.hesba.nativeapp',
    [int]$VersionCode = 1,
    [string]$VersionName = '3.0.0-preview'
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$tools = Join-Path $root '.tools'
$jdk = Join-Path $tools 'jdk'
$buildTools = Join-Path $tools 'android-sdk\build-tools\35.0.0'
$androidJar = Join-Path $tools 'android-sdk\platforms\android-36\android.jar'
$kotlinc = Join-Path $tools 'kotlinc\bin\kotlinc.bat'
$kotlinStdlib = Join-Path $tools 'kotlinc\lib\kotlin-stdlib.jar'

$required = @{
    'JDK (javac)'          = "$jdk\bin\javac.exe"
    'build-tools (aapt2)'  = "$buildTools\aapt2.exe"
    'android.jar (API 36)' = $androidJar
    'kotlinc'              = $kotlinc
    'kotlin stdlib'        = $kotlinStdlib
}
foreach ($name in $required.Keys) {
    if (!(Test-Path $required[$name])) {
        throw "Missing tool [$name]: $($required[$name]) -- run .tools\get-toolchain.ps1 first"
    }
}

$out = Join-Path $root 'build\out'
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
foreach ($dir in @('gen', 'rclasses', 'classes', 'dex', 'apk')) {
    New-Item (Join-Path $out $dir) -ItemType Directory -Force | Out-Null
}

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"
$enc = New-Object System.Text.UTF8Encoding($false)

function Step($message) { Write-Output "==> $message" }

# 1) manifest copy that carries the package name required by the manual toolchain
Step 'preparing manifest'
$manifestSource = Join-Path $root 'app\src\main\AndroidManifest.xml'
$manifest = [System.IO.File]::ReadAllText($manifestSource)
$anchor = '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
if ($manifest -notmatch 'package=') {
    $manifest = $manifest.Replace($anchor, '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="' + $PackageName + '">')
}
$manifestPath = Join-Path $out 'AndroidManifest.xml'
[System.IO.File]::WriteAllText($manifestPath, $manifest, $enc)

# 2) resources
Step 'aapt2 compile'
& "$buildTools\aapt2.exe" compile --dir (Join-Path $root 'app\src\main\res') -o (Join-Path $out 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Step 'aapt2 link'
& "$buildTools\aapt2.exe" link `
    -o (Join-Path $out 'base.apk') `
    -I $androidJar `
    --manifest $manifestPath `
    -R (Join-Path $out 'res.zip') `
    --java (Join-Path $out 'gen') `
    --min-sdk-version 23 `
    --target-sdk-version 36 `
    --version-code $VersionCode `
    --version-name $VersionName `
    --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

# 3) R.java
Step 'javac R.java'
$rJava = Get-ChildItem (Join-Path $out 'gen') -Recurse -Filter 'R.java' |
    Select-Object -First 1 -ExpandProperty FullName
& "$jdk\bin\javac.exe" -encoding UTF-8 -nowarn -classpath $androidJar -d (Join-Path $out 'rclasses') $rJava
if ($LASTEXITCODE -ne 0) { throw 'javac R.java failed' }

# 4) Kotlin sources of both modules (invoked through java so the classpath keeps its separators)
Step 'kotlinc'
$kotlinCompilerJar = Join-Path $tools 'kotlinc\lib\kotlin-compiler.jar'
if (!(Test-Path $kotlinCompilerJar)) { throw "Missing kotlin-compiler.jar: $kotlinCompilerJar" }
$compileClasspath = "$androidJar;$(Join-Path $out 'rclasses')"
& "$jdk\bin\java.exe" -Xmx1024m -cp $kotlinCompilerJar org.jetbrains.kotlin.cli.jvm.K2JVMCompiler `
    -nowarn -jvm-target 17 `
    -classpath $compileClasspath `
    -d (Join-Path $out 'classes') `
    (Join-Path $root 'core\src\main\kotlin') `
    (Join-Path $root 'app\src\main\kotlin')
if ($LASTEXITCODE -ne 0) { throw 'kotlinc failed' }

# 5) dex (app classes + kotlin runtime)
Step 'd8'
& "$jdk\bin\jar.exe" cf (Join-Path $out 'classes.jar') -C (Join-Path $out 'classes') .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
& "$buildTools\d8.bat" --lib $androidJar --min-api 23 --output (Join-Path $out 'dex') `
    (Join-Path $out 'classes.jar') $kotlinStdlib
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

# 6) put classes.dex inside the resource apk
Step 'packaging'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$unsigned = Join-Path $out 'apk\unsigned.apk'
Copy-Item (Join-Path $out 'base.apk') $unsigned -Force
$zip = [System.IO.Compression.ZipFile]::Open($unsigned, 'Update')
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
    $zip, (Join-Path $out 'dex\classes.dex'), 'classes.dex') | Out-Null
$zip.Dispose()

# 7) align + sign
Step 'zipalign'
$aligned = Join-Path $out 'apk\aligned.apk'
& "$buildTools\zipalign.exe" -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$keystore = Join-Path $root 'keystore\preview.keystore'
if (!(Test-Path $keystore)) {
    Step 'creating preview keystore'
    New-Item (Split-Path $keystore -Parent) -ItemType Directory -Force | Out-Null
    & "$jdk\bin\keytool.exe" -genkeypair -v -keystore $keystore -storepass android `
        -keypass android -alias previewkey -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=7esba preview, O=7esba, C=EG"
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}

Step 'apksigner'
$apk = Join-Path $root ("build\7esba-preview-" + $VersionCode + ".apk")
& "$buildTools\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android `
    --ks-key-alias previewkey --v1-signing-enabled true --v2-signing-enabled true `
    --out $apk $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }
& "$buildTools\apksigner.bat" verify --print-certs $apk

Write-Output ''
Write-Output "APK ready : $apk"
Write-Output "size      : $([math]::Round((Get-Item $apk).Length / 1KB, 0)) KB"
Write-Output "package   : $PackageName (preview only, not the Play identity)"