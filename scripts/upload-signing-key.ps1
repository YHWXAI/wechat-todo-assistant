param(
    [Parameter(Mandatory = $true)][string]$KeystorePath,
    [string]$Repository = 'YHWXAI/wechat-todo-assistant'
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'Install GitHub CLI first: winget install --id GitHub.cli --exact'
}
if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) { throw 'Keystore file not found.' }
gh auth status
if ($LASTEXITCODE -ne 0) { throw 'Sign in first with: gh auth login --web' }

function Set-EncryptedSecret([string]$Name, [string]$Value) {
    # gh encrypts the value before sending it; stdin keeps it out of process arguments.
    $Value | gh secret set $Name --repo $Repository
    if ($LASTEXITCODE -ne 0) { throw "Could not upload $Name" }
}

function Read-PrivateValue([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

$encoded = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $KeystorePath).Path))
try {
    Set-EncryptedSecret 'ANDROID_KEYSTORE_BASE64' $encoded
    $storePassword = Read-PrivateValue 'Keystore password (hidden)'
    $alias = Read-Host 'Key alias (standard Android debug key: androiddebugkey)'
    $keyPassword = Read-PrivateValue 'Key password (hidden)'
    if (-not $storePassword -or -not $alias -or -not $keyPassword) { throw 'Values cannot be empty.' }
    Set-EncryptedSecret 'ANDROID_KEYSTORE_PASSWORD' $storePassword
    Set-EncryptedSecret 'ANDROID_KEY_ALIAS' $alias
    Set-EncryptedSecret 'ANDROID_KEY_PASSWORD' $keyPassword
    Write-Host 'Encrypted signing secrets uploaded. Keep a secure backup until a cloud release is verified.'
} finally {
    $encoded = $null
    $storePassword = $null
    $keyPassword = $null
}
