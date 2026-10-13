# 역할: 분석 후 사용자의 명시적 승인을 거친 경우에만, 임시 스테이징의 신규/수정 파일을 로컬 프로젝트에 안전하게 덮어쓰고(옵션 A) 임시 파일을 정리하는 PowerShell 스크립트

param (
    [Parameter(Mandatory=$true)]
    [string]$MetadataPath
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $MetadataPath)) {
    Write-Error "Metadata file not found: $MetadataPath"
    exit 1
}

$rawJson = [System.IO.File]::ReadAllText($MetadataPath, [System.Text.Encoding]::UTF8)
$meta = $rawJson | ConvertFrom-Json

$zipRoot = $meta.ZipRoot
$projectRoot = $meta.ProjectRoot
$tempBase = $meta.TempBase

if (-not (Test-Path $zipRoot)) {
    Write-Error "Staging directory not found: $zipRoot"
    exit 1
}

Write-Host "=================================================="
Write-Host "Applying Clean ZIP Changes (Step 2: Apply upon approval)"
Write-Host "=================================================="
Write-Host "- Source Staging : $zipRoot"
Write-Host "- Target Project : $projectRoot"
Write-Host "- Added Count    : $($meta.AddedCount)"
Write-Host "- Modified Count : $($meta.ModifiedCount)"
Write-Host ""

# 보호 필터 정의
$excludeDirNames = @('.git', '.gradle', '.idea', '.kotlin', '.grok', 'build')
$excludeFileNames = @('local.properties')
$excludeExtensions = @('.iml', '.pyc', '.zip')

$stagingFiles = Get-ChildItem -Path $zipRoot -Recurse -File -Force

$copiedCount = 0
$skippedCount = 0

foreach ($file in $stagingFiles) {
    if ($file.Name -eq "import_metadata.json") { continue }

    $relPath = $file.FullName.Substring($zipRoot.Length).TrimStart('\', '/')
    $parts = $relPath.Split([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)

    # 보호 파일 검사
    $isProtected = $false
    foreach ($part in $parts) {
        if ($excludeDirNames -contains $part) { $isProtected = $true; break }
    }
    if ($relPath -match "(^|[/\\])build([/\\]|$)") { $isProtected = $true }
    if ($excludeFileNames -contains $file.Name) { $isProtected = $true }
    if ($excludeExtensions -contains $file.Extension) { $isProtected = $true }
    if ($parts.Count -eq 1 -and $file.Extension -eq '.xml') { $isProtected = $true }

    if ($isProtected) {
        $skippedCount++
        continue
    }

    $destPath = Join-Path $projectRoot $relPath
    $destDir = Split-Path $destPath -Parent

    if (-not (Test-Path $destDir)) {
        New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    }

    Copy-Item -Path $file.FullName -Destination $destPath -Force
    $copiedCount++
}

Write-Host "• Successfully copied: $copiedCount files"
Write-Host "• Safely protected  : $skippedCount files"
Write-Host ""

# 임시 폴더 정리
try {
    if (Test-Path $tempBase) {
        Remove-Item -Path $tempBase -Recurse -Force
        Write-Host "• Cleaned up temporary staging directory."
    }
} catch {
    Write-Warning "Could not clean temporary folder: $tempBase"
}

Write-Host ""
Write-Host "=================================================="
Write-Host "Clean ZIP synchronization completed successfully!"
Write-Host "=================================================="