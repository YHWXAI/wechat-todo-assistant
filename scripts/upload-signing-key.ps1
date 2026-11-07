param(
    [string]$KeystorePath,
    [string]$Repository = 'YHWXAI/wechat-todo-assistant',
    [switch]$UseStandardDebugKey
)
$ErrorActionPreference = 'Stop'
if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'Invalid repository name.' }
if (-not $KeystorePath) {
    $candidates = @()
    if ($PSScriptRoot -and (Test-Path (Join-Path $PSScriptRoot '..\.android-tools'))) {
        $candidates = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '..\.android-tools') -Filter debug.keystore -File -Recurse)
    }
    if ($candidates.Count -eq 1) { $KeystorePath = $candidates[0].FullName }
    else {
        Add-Type -AssemblyName System.Windows.Forms
        $dialog = New-Object System.Windows.Forms.OpenFileDialog
        $dialog.Title = 'Select the ORIGINAL debug.keystore used by the installed app'
        $dialog.Filter = 'Android signing key|*.keystore;*.jks'
        try {
            if ($dialog.ShowDialog() -ne 'OK') { throw 'No key selected; nothing uploaded.' }
            $KeystorePath = $dialog.FileName
        } finally { $dialog.Dispose() }
    }
}
if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) { throw 'Keystore file not found.' }

$ghCommand = Get-Command gh -ErrorAction SilentlyContinue
if ($ghCommand) { $ghExe = $ghCommand.Source }
else {
    # Official portable release: no Git, administrator rights or installation required.
    $arch = 'amd64'
    $expected = '98d0cfa794721665d7973dd728c78aff7bd836d58f10f6d7a03568eccb0a22e0'
    if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64' -or $env:PROCESSOR_ARCHITEW6432 -eq 'ARM64') {
        $arch = 'arm64'
        $expected = '197211c98687d7770ac331fe8a3b6c198c389108c1536cdbedf659fe4703e484'
    }
    $tools = Join-Path $env:TEMP ('wechat-signing-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $tools | Out-Null
    $zip = Join-Path $tools 'gh.zip'
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    Write-Host 'Downloading the official portable GitHub CLI and verifying SHA-256...'
    Invoke-WebRequest -UseBasicParsing -Uri "https://github.com/cli/cli/releases/download/v2.103.3/gh_2.103.3_windows_$arch.zip" -OutFile $zip
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash.ToLowerInvariant() -ne $expected) {
        throw 'GitHub CLI checksum mismatch; refusing to execute it.'
    }
    Expand-Archive -LiteralPath $zip -DestinationPath $tools
    $executables = @(Get-ChildItem -LiteralPath $tools -Filter gh.exe -File -Recurse)
    if ($executables.Count -ne 1) { throw 'Unexpected GitHub CLI archive structure.' }
    $ghExe = $executables[0].FullName
}

& $ghExe auth status --hostname github.com
if ($LASTEXITCODE -ne 0) {
    & $ghExe auth login --hostname github.com --web --git-protocol https --scopes repo
    if ($LASTEXITCODE -ne 0) { throw 'GitHub sign-in did not finish.' }
}
# Check the exact repository permission before reading/uploading key material.
& $ghExe secret list --repo $Repository
if ($LASTEXITCODE -ne 0) { throw 'Cannot manage Actions secrets for this repository.' }

function Set-EncryptedSecret([string]$Name, [string]$Value) {
    # Exact stdin, without a trailing newline or putting secret values in arguments.
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $ghExe
    $start.Arguments = "secret set $Name --repo $Repository"
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    $process = [Diagnostics.Process]::Start($start)
    try {
        $process.StandardInput.Write($Value)
        $process.StandardInput.Close()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "Could not upload $Name" }
    } finally { $process.Dispose() }
}
function Read-PrivateValue([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

try {
    if ($UseStandardDebugKey) {
        $storePassword = 'android'; $alias = 'androiddebugkey'; $keyPassword = 'android'
    } else {
        $storePassword = Read-PrivateValue 'Keystore password (hidden)'
        $alias = Read-Host 'Key alias (standard Android debug key: androiddebugkey)'
        $keyPassword = Read-PrivateValue 'Key password (hidden)'
    }
    if (-not $storePassword -or -not $alias -or -not $keyPassword) { throw 'Values cannot be empty.' }
    $encoded = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $KeystorePath).Path))
    Set-EncryptedSecret 'ANDROID_KEYSTORE_BASE64' $encoded
    Set-EncryptedSecret 'ANDROID_KEYSTORE_PASSWORD' $storePassword
    Set-EncryptedSecret 'ANDROID_KEY_ALIAS' $alias
    Set-EncryptedSecret 'ANDROID_KEY_PASSWORD' $keyPassword
    Write-Host 'Encrypted secrets uploaded. Starting cloud signing verification (no release)...'
    $verification = [Guid]::NewGuid().ToString('N')
    & $ghExe workflow run verify-signing.yml --ref main --repo $Repository -f "verification_id=$verification"
    if ($LASTEXITCODE -ne 0) { throw 'Secrets uploaded, but cloud verification could not start.' }
    $runId = $null
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $runsJson = & $ghExe run list --workflow verify-signing.yml --event workflow_dispatch --repo $Repository --limit 30 --json databaseId,displayTitle
        if ($LASTEXITCODE -ne 0) { throw 'Cannot read cloud verification status.' }
        $run = @(($runsJson | ConvertFrom-Json) | Where-Object { $_.displayTitle -eq "Verify signing $verification" })
        if ($run.Count -eq 1) { $runId = $run[0].databaseId; break }
        Start-Sleep -Seconds 2
    }
    if (-not $runId) { throw 'Verification started, but no matching run was found. Check GitHub Actions.' }
    & $ghExe run watch $runId --repo $Repository --exit-status
    if ($LASTEXITCODE -ne 0) { throw 'Cloud signing verification failed. Keep your original key backup.' }
    Write-Host 'SUCCESS: encrypted key is hosted in GitHub Actions and the original app signature was verified.'
    Write-Host 'Keep a separate encrypted backup; this script never deletes your original key.'
} finally {
    $encoded = $null; $storePassword = $null; $keyPassword = $null
}
