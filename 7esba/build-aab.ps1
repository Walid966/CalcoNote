param([int]$VersionCode=3,[string]$VersionName="3.0.0")
$ErrorActionPreference="Stop"
$root = "f:\الة حاسبه مع نوت\7esba"
$tools = Join-Path $root ".tools"
$jdk = Join-Path $tools "jdk"
$bt = Join-Path $tools "android-sdk\build-tools\35.0.0"
$aj = Join-Path $tools "android-sdk\platforms\android-36\android.jar"
$ks = Join-Path $tools "kotlinc\lib\kotlin-stdlib.jar"
$btl = Join-Path $tools "bundletool.jar"
$out = Join-Path $root "build\out"

Write-Output "==> aab build (package=io.github.walid966.twa, versionCode=$VersionCode, versionName=$VersionName)"

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item "$out\gen" -ItemType Directory -Force | Out-Null
New-Item "$out\rclasses" -ItemType Directory -Force | Out-Null
New-Item "$out\classes" -ItemType Directory -Force | Out-Null
New-Item "$out\dex" -ItemType Directory -Force | Out-Null
$env:JAVA_HOME=$jdk
$env:PATH="$jdk\bin;$env:PATH"

Write-Output "==> aapt2 compile"
& "$bt\aapt2.exe" compile --dir "$root\app\src\main\res" -o "$out\res.zip"
if ($LASTEXITCODE) { throw "aapt2 compile failed" }

Write-Output "==> aapt2 link (proto)"
$man = Get-Content "$root\app\src\main\AndroidManifest.xml" -Raw
$man = $man -replace '<manifest xmlns:android="http://schemas.android.com/apk/res/android">','<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="io.github.walid966.twa">'
[System.IO.File]::WriteAllText("$out\AndroidManifest.xml", $man, [System.Text.Encoding]::UTF8)
& "$bt\aapt2.exe" link --proto-format -I $aj --manifest "$out\AndroidManifest.xml" -R "$out\res.zip" --java "$out\gen" --min-sdk-version 23 --target-sdk-version 36 --version-code $VersionCode --version-name $VersionName --custom-package com.hesba.nativeapp --auto-add-overlay -o "$out\base.zip"
if ($LASTEXITCODE) { throw "aapt2 link failed" }

Write-Output "==> javac R.java"
$rj = Get-ChildItem "$out\gen" -Recurse -Filter R.java | Select-Object -First 1 -ExpandProperty FullName
& "$jdk\bin\javac.exe" -encoding UTF-8 -nowarn -classpath $aj -d "$out\rclasses" $rj
if ($LASTEXITCODE) { throw "javac failed" }

Write-Output "==> kotlinc"
& "$jdk\bin\java.exe" -Xmx1024m -cp "$tools\kotlinc\lib\kotlin-compiler.jar" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn -jvm-target 17 -classpath "$aj;$out\rclasses" -d "$out\classes" "$root\core\src\main\kotlin" "$root\app\src\main\kotlin"
if ($LASTEXITCODE) { throw "kotlinc failed" }

Write-Output "==> d8"
& "$jdk\bin\jar.exe" cf "$out\classes.jar" -C "$out\classes" .
if ($LASTEXITCODE) { throw "jar failed" }
& "$bt\d8.bat" --lib $aj --min-api 23 --output "$out\dex" "$out\classes.jar" $ks
if ($LASTEXITCODE) { throw "d8 failed" }

Write-Output "==> packaging"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$out\base.zip", "Update")
$oldEntry = $zip.Entries | Where-Object { $_.FullName -eq "AndroidManifest.xml" }
if ($oldEntry) {
    $stream = $oldEntry.Open()
    $newEntry = $zip.CreateEntry("manifest/AndroidManifest.xml", [System.IO.Compression.CompressionLevel]::Optimal)
    $newStream = $newEntry.Open()
    $stream.CopyTo($newStream)
    $stream.Dispose(); $newStream.Dispose()
    $oldEntry.Delete()
    Write-Output "    moved AndroidManifest.xml -> manifest/AndroidManifest.xml"
}
$df = Get-ChildItem "$out\dex" -Filter "classes*.dex" | Select-Object -First 1 -ExpandProperty FullName
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $df, "dex/classes.dex") | Out-Null
$zip.Dispose()

Write-Output "==> bundletool build-bundle"
$aab = "$root\build\7esba-$VersionName-$VersionCode.aab"
& "$jdk\bin\java.exe" -jar $btl build-bundle --modules="$out\base.zip" --output="$out\7esba-unsigned.aab" --overwrite
if ($LASTEXITCODE) { throw "bundletool build-bundle failed" }

Write-Output "==> jarsigner sign"
$keystore = "$root\..\google-play-packageV2\signing.keystore"
& "$jdk\bin\jarsigner.exe" -keystore $keystore -storepass L3iiaSJKFkXD -keypass L3iiaSJKFkXD -signedjar $aab "$out\7esba-unsigned.aab" my-key-alias
if ($LASTEXITCODE) { throw "jarsigner failed" }

Write-Output "==> bundletool validate"
& "$jdk\bin\java.exe" -jar $btl validate --bundle=$aab
if ($LASTEXITCODE) { throw "validate failed" }

Write-Output "==> SUCCESS: $aab"
