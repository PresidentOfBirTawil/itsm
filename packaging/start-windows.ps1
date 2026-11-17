# Started by "Start Quell.bat". Finds or downloads Java 25, builds Quell if needed, and starts it.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Write-Step([string] $text) { Write-Host "  $text" }

function Get-JavaMajor([string] $javaExe) {
    # "java -version" writes to the error stream. With 'Stop', Windows PowerShell 5.1 would treat that as a failure.
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& $javaExe -version 2>&1 | ForEach-Object { "$_" }) -join "`n"
    } catch {
        return 0
    }
    if ($output -match 'version "(\d+)') { return [int] $Matches[1] }
    return 0
}

function Find-LocalJava([string] $folder) {
    $jdk = Get-ChildItem $folder -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
    if ($jdk) { return (Join-Path $jdk.FullName 'bin\java.exe') }
    return $null
}

Write-Host ''
Write-Host '  Quell' -ForegroundColor Cyan
Write-Host '  -----'

# 1. Java 25: a private copy from an earlier start, the one on this computer, or a fresh download.
$runtime = Join-Path $root '.runtime'
$java = Find-LocalJava $runtime
if (-not $java) {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }
    foreach ($candidate in $candidates) {
        if ((Test-Path $candidate) -and ((Get-JavaMajor $candidate) -ge 25)) { $java = $candidate; break }
    }
}
if (-not $java) {
    Write-Step 'Java 25 is not on this computer. Downloading a private copy into this folder (once, about 200 MB)...'
    New-Item -ItemType Directory -Force -Path $runtime | Out-Null
    $architecture = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'aarch64' } else { 'x64' }
    $url = "https://api.adoptium.net/v3/binary/latest/25/ga/windows/$architecture/jdk/hotspot/normal/eclipse"
    $zip = Join-Path $runtime 'jdk.zip'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    Write-Step 'Unpacking Java...'
    Expand-Archive -Path $zip -DestinationPath $runtime -Force
    Remove-Item $zip
    $java = Find-LocalJava $runtime
    if (-not $java) { throw 'The Java download could not be unpacked.' }
}
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java)
Write-Step "Using Java from $env:JAVA_HOME"

# 2. Build Quell the first time, and again whenever the code is newer than the last build.
$jar = Join-Path $root 'target\quell.jar'
$newest = Get-ChildItem -Path (Join-Path $root 'src'), (Join-Path $root 'pom.xml') -Recurse -File |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not (Test-Path $jar) -or ($newest.LastWriteTime -gt (Get-Item $jar).LastWriteTime)) {
    Write-Step 'Building Quell. The first time this downloads its parts and takes a few minutes...'
    & (Join-Path $root 'mvnw.cmd') -q -B -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'The build failed. See the messages above.' }
}

# 3. Start it. It opens the browser by itself when it is ready.
Write-Step 'Starting... your browser opens in a few seconds.'
Write-Step 'Keep this window open while you use Quell. Close it to stop Quell.'
Write-Host ''
& $java '-Dapp.desktop=true' "-Dapp.data-dir=$root\data" -jar $jar
