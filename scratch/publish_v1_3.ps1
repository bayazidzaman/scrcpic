param(
    [switch]$PublishToGitHub = $false
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Scrcpic Dual-App Release Builder (v1.3.0)" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Ensure output directory
$artifactDir = "release_artifacts"
if (-not (Test-Path $artifactDir)) {
    New-Item -ItemType Directory -Path $artifactDir | Out-Null
}

# 2. Compile release APKs
Write-Host "`n[1/3] Building APKs for :app (v1.3.0) and :beacon (v1.0.0)..." -ForegroundColor Yellow
./gradlew :app:assembleRelease :beacon:assembleRelease --no-daemon
if ($LASTEXITCODE -ne 0) {
    throw "Gradle release build failed."
}

# 3. Copy and rename artifacts
$appSrc = "app/build/outputs/apk/release/app-release.apk"
$beaconSrc = "beacon/build/outputs/apk/release/beacon-release.apk"

$appDest = "$artifactDir/Scrcpic-v1.3.0.apk"
$beaconDest = "$artifactDir/Scrcpic-Beacon-v1.0.0.apk"

Copy-Item -Force $appSrc $appDest
Copy-Item -Force $beaconSrc $beaconDest

Write-Host "`n[2/3] Artifacts successfully prepared locally:" -ForegroundColor Green
Get-Item $appDest, $beaconDest | Select-Object Name, Length, LastWriteTime | Format-Table -AutoSize

if (-not $PublishToGitHub) {
    Write-Host "`n[3/3] Local build and packaging complete!" -ForegroundColor Cyan
    Write-Host "Artifacts are stored in: $artifactDir" -ForegroundColor Cyan
    Write-Host "To publish to GitHub, run this script with -PublishToGitHub after user approval." -ForegroundColor Gray
    exit 0
}

# 4. GitHub Release publishing (Requires explicit user trigger)
Write-Host "`n[3/3] Publishing to GitHub (bayazidzaman/scrcpic)..." -ForegroundColor Yellow

# Get Token from git credential helper
$inputStr = "protocol=https`nhost=github.com`n`n"
$pinfo = New-Object System.Diagnostics.ProcessStartInfo
$pinfo.FileName = "git.exe"
$pinfo.Arguments = "credential fill"
$pinfo.UseShellExecute = $false
$pinfo.RedirectStandardInput = $true
$pinfo.RedirectStandardOutput = $true
$pinfo.CreateNoWindow = $true

$proc = [System.Diagnostics.Process]::Start($pinfo)
$proc.StandardInput.Write($inputStr)
$proc.StandardInput.Flush()
$proc.StandardInput.Close()
$output = $proc.StandardOutput.ReadToEnd()
$proc.WaitForExit()

$token = ""
foreach ($line in ($output -split "`n")) {
    $trimmed = $line.Trim()
    if ($trimmed.StartsWith("password=")) {
        $token = $trimmed.Substring("password=".Length)
    }
}

if (-not $token) {
    throw "No GitHub token found in git credential helper."
}

$releaseBody = @"
# 📱 Scrcpic v1.3.0 — Dual App Release & Broken Screen Recovery

We are excited to announce **Scrcpic v1.3.0**, introducing a breakthrough architecture for broken-screen Android recovery and dynamic hotspot environments.

---

### 🌟 What's New:

- 📡 **On-Demand Beacon Auto-Discovery:**
  - Discover your target device with a single tap on any Wi-Fi network or Mobile Hotspot.
  - Automatically receives the device model and assigned IP address within milliseconds.
  - No more guessing or manually looking up changing dynamic DHCP IPs!

- 🪶 **New Headless Companion App — Scrcpic Beacon (v1.0.0):**
  - Ultra-lightweight (~50KB), 0-UI background listener tailored specifically for shattered touchscreen phones.
  - Runs silently, auto-starts on boot, consumes 0% CPU, and answers discovery queries strictly on-demand.
  - Zero dangerous permissions: no screen recording prompt, no root, and no accessibility service required.

- 📂 **Enhanced Remote File Manager:**
  - Clean path navigation and native remote file opening for photos, videos, and PDFs.

- 🎨 **Polished Discovery UI:**
  - Dedicated radar screen featuring real-time response latency, network status indicators, and instant 1-tap connection.

---

### 📦 Downloadable Assets:
1. **`Scrcpic-v1.3.0.apk`** — The primary Controller app (install on your healthy phone).
2. **`Scrcpic-Beacon-v1.0.0.apk`** — The headless companion (install once on the broken-screen device).
"@

$releaseBodyEscaped = $releaseBody.Replace('\', '\\').Replace('"', '\"').Replace("`n", "\n").Replace("`r", "")
$releaseDataJson = @"
{
  "tag_name": "v1.3.0",
  "target_commitish": "main",
  "name": "Scrcpic v1.3.0",
  "body": "$releaseBodyEscaped",
  "draft": false,
  "prerelease": false
}
"@

$repo = "bayazidzaman/scrcpic"
$releaseUrl = "https://api.github.com/repos/$repo/releases"

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$bytes = [System.Text.Encoding]::UTF8.GetBytes($releaseDataJson)

$request = [System.Net.WebRequest]::Create($releaseUrl)
$request.Method = "POST"
$request.ContentType = "application/json; charset=utf-8"
$request.Headers.Add("Authorization", "Bearer $token")
$request.Accept = "application/vnd.github+json"
$request.UserAgent = "Scrcpic-Release-Tool"

$stream = $request.GetRequestStream()
$stream.Write($bytes, 0, $bytes.Length)
$stream.Close()

$response = $request.GetResponse()
$reader = New-Object System.IO.StreamReader($response.GetResponseStream())
$jsonResponse = $reader.ReadToEnd() | ConvertFrom-Json
$uploadUrl = $jsonResponse.upload_url -replace '\{.*?\}', ''
Write-Host "Release created successfully. Upload URL: $uploadUrl" -ForegroundColor Green

# Upload both APKs
$uploadHeaders = @{
    "Authorization" = "Bearer $token"
    "Accept"        = "application/vnd.github+json"
    "User-Agent"    = "Scrcpic-Release-Tool"
    "Content-Type"  = "application/vnd.android.package-archive"
}

Write-Host "Uploading Scrcpic-v1.3.0.apk..." -ForegroundColor Yellow
$uri1 = "$uploadUrl`?name=Scrcpic-v1.3.0.apk"
Invoke-RestMethod -Uri $uri1 -Method Post -Headers $uploadHeaders -InFile $appDest

Write-Host "Uploading Scrcpic-Beacon-v1.0.0.apk..." -ForegroundColor Yellow
$uri2 = "$uploadUrl`?name=Scrcpic-Beacon-v1.0.0.apk"
Invoke-RestMethod -Uri $uri2 -Method Post -Headers $uploadHeaders -InFile $beaconDest

Write-Host "`n🎉 Both APKs successfully published to GitHub Release v1.3.0!" -ForegroundColor Green
