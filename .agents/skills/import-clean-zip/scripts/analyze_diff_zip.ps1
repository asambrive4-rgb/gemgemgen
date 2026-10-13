# 역할: 첨부된 ZIP 아카이브를 임시 폴더에 해제하고, 로컬 프로젝트와의 차이점(신규/수정 파일 및 미커밋 상태)을 비교 분석하여 승인 전 리포트를 생성하는 PowerShell 스크립트

param (
    [Parameter(Mandatory=$true)]
    [string]$ZipPath,
    [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

# 1. 경로 정규화 및 검증
if (-not (Test-Path $ZipPath)) {
    Write-Error "ZIP file not found at: $ZipPath"
    exit 1
}
$ZipPath = [System.IO.Path]::GetFullPath($ZipPath)

if (-not $ProjectRoot) {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $candidate = Resolve-Path (Join-Path $scriptDir "..\..\..\..") -ErrorAction SilentlyContinue
    if ($candidate -and (Test-Path (Join-Path $candidate.Path "build.gradle.kts"))) {
        $ProjectRoot = $candidate.Path
    } else {
        $ProjectRoot = (Get-Location).Path
    }
}
$ProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot)

Write-Host "=================================================="
Write-Host "ZIP Archive Diff Analysis (Step 1: Inspect Only)"
Write-Host "=================================================="
Write-Host "- Zip File     : $ZipPath"
Write-Host "- Target Project: $ProjectRoot"
Write-Host ""

# 2. 로컬 Git 미커밋 작업 점검
$gitStatus = @()
try {
    $gitOutput = git -C $ProjectRoot status --porcelain 2>$null
    if ($gitOutput) {
        $gitStatus = @($gitOutput | Where-Object { $_.Trim() -ne "" })
    }
} catch {
    # git 없을 경우 무시
}

if ($gitStatus.Count -gt 0) {
    Write-Host "[WARNING] Uncommitted changes detected in local project ($($gitStatus.Count) files)!" -ForegroundColor Yellow
    Write-Host "  Please ensure you review or stash your work before applying external zip changes." -ForegroundColor Yellow
    Write-Host ""
}

# 3. 임시 폴더 생성 및 압축 해제
$tempBase = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "gemgemgen_import_" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempBase -Force | Out-Null

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

[System.IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $tempBase)

# 4. ZIP 내부 프로젝트 루트 자동 감지 (Flat vs Wrapped)
$zipRoot = $tempBase
if (-not (Test-Path (Join-Path $tempBase "settings.gradle.kts")) -and -not (Test-Path (Join-Path $tempBase "app"))) {
    # 1단계 하위 폴더 검색
    $subDirs = Get-ChildItem -Path $tempBase -Directory
    foreach ($sub in $subDirs) {
        if ((Test-Path (Join-Path $sub.FullName "settings.gradle.kts")) -or (Test-Path (Join-Path $sub.FullName "app"))) {
            $zipRoot = $sub.FullName
            break
        }
    }
}

Write-Host "- Detected Zip Root: $zipRoot"
Write-Host ""

# 5. 보호 및 제외 필터 정의
$excludeDirNames = @('.git', '.gradle', '.idea', '.kotlin', '.grok', 'build')
$excludeFileNames = @('local.properties')
$excludeExtensions = @('.iml', '.pyc', '.zip')

# 6. ZIP 파일 순회 및 로컬 파일과 비교
$zipFiles = Get-ChildItem -Path $zipRoot -Recurse -File -Force

$addedFiles = [System.Collections.Generic.List[PSCustomObject]]::new()
$modifiedFiles = [System.Collections.Generic.List[PSCustomObject]]::new()
$identicalFiles = [System.Collections.Generic.List[PSCustomObject]]::new()
$skippedProtected = [System.Collections.Generic.List[string]]::new()

foreach ($file in $zipFiles) {
    $relPath = $file.FullName.Substring($zipRoot.Length).TrimStart('\', '/')
    $parts = $relPath.Split([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)

    # 보호 파일 검사
    $isExcluded = $false
    foreach ($part in $parts) {
        if ($excludeDirNames -contains $part) { $isExcluded = $true; break }
    }
    if ($relPath -match "(^|[/\\])build([/\\]|$)") { $isExcluded = $true }
    if ($excludeFileNames -contains $file.Name) { $isExcluded = $true }
    if ($excludeExtensions -contains $file.Extension) { $isExcluded = $true }
    if ($parts.Count -eq 1 -and $file.Extension -eq '.xml') { $isExcluded = $true }

    if ($isExcluded) {
        $skippedProtected.Add($relPath)
        continue
    }

    $localPath = Join-Path $ProjectRoot $relPath
    if (-not (Test-Path $localPath)) {
        $addedFiles.Add([PSCustomObject]@{
            RelativePath = $relPath
            SizeKB = [math]::Round($file.Length / 1KB, 2)
        })
    } else {
        # 내용 일치 비교 (MD5 해시)
        $zipHash = (Get-FileHash -Path $file.FullName -Algorithm MD5).Hash
        $localHash = (Get-FileHash -Path $localPath -Algorithm MD5).Hash
        
        if ($zipHash -ne $localHash) {
            $localItem = Get-Item $localPath
            $modifiedFiles.Add([PSCustomObject]@{
                RelativePath = $relPath
                ZipSizeKB = [math]::Round($file.Length / 1KB, 2)
                LocalSizeKB = [math]::Round($localItem.Length / 1KB, 2)
            })
        } else {
            $identicalFiles.Add([PSCustomObject]@{
                RelativePath = $relPath
            })
        }
    }
}

# 7. 분석 결과 출력
Write-Host "=================================================="
Write-Host "Analysis Summary (Option A: Add/Modify Only)"
Write-Host "=================================================="
Write-Host "• Added Files (New)       : $($addedFiles.Count)"
Write-Host "• Modified Files (Changed) : $($modifiedFiles.Count)"
Write-Host "• Unchanged Files (Same)   : $($identicalFiles.Count)"
Write-Host "• Protected/Skipped Items  : $($skippedProtected.Count)"
Write-Host ""

if ($addedFiles.Count -gt 0) {
    Write-Host "[Added Files - New]" -ForegroundColor Green
    $addedFiles | Select-Object -First 15 | Format-Table -AutoSize
    if ($addedFiles.Count -gt 15) { Write-Host "... and $($addedFiles.Count - 15) more" }
}

if ($modifiedFiles.Count -gt 0) {
    Write-Host "[Modified Files - Changed]" -ForegroundColor Cyan
    $modifiedFiles | Select-Object -First 15 | Format-Table -AutoSize
    if ($modifiedFiles.Count -gt 15) { Write-Host "... and $($modifiedFiles.Count - 15) more" }
}

if ($addedFiles.Count -eq 0 -and $modifiedFiles.Count -eq 0) {
    Write-Host "No differences found between ZIP and local project!" -ForegroundColor Yellow
}

# 8. 메타데이터 파일 생성 (적용 스크립트용)
$meta = @{
    ZipPath = $ZipPath
    ZipRoot = $zipRoot
    TempBase = $tempBase
    ProjectRoot = $ProjectRoot
    AddedCount = $addedFiles.Count
    ModifiedCount = $modifiedFiles.Count
    UncommittedGitFiles = $gitStatus
} | ConvertTo-Json

$metaPath = Join-Path $tempBase "import_metadata.json"
[System.IO.File]::WriteAllText($metaPath, $meta, [System.Text.Encoding]::UTF8)

Write-Host ""
Write-Host "Staging Directory Ready: $zipRoot" -ForegroundColor DarkGray
Write-Host "Metadata Saved To     : $metaPath" -ForegroundColor DarkGray
Write-Host ""
Write-Host ">> Do NOT apply automatically! Present changes to user and ask for approval first." -ForegroundColor Magenta