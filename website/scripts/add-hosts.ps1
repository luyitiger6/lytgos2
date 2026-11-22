# Adds the local development domain to the Windows hosts file.
# Run this script from an elevated PowerShell session.

$hostsFile = Join-Path $env:SystemRoot 'System32\drivers\etc\hosts'
$entry = '127.0.0.1 lytiger.lytgos2.com'

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Error 'Administrator rights are required. Right-click PowerShell and choose "Run as administrator", then run this script again.'
    exit 1
}

if (Select-String -Path $hostsFile -Pattern 'lytiger.lytgos2.com' -SimpleMatch -Quiet) {
    Write-Output 'Hosts entry already exists: lytiger.lytgos2.com'
    exit 0
}

Add-Content -Path $hostsFile -Value "`r`n# LytgOS2 local development`r`n$entry" -Encoding ascii
Write-Output "Added: $entry"
Write-Output 'Open http://lytiger.lytgos2.com:5173/ after starting npm run docs:dev.'
