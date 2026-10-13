# 역할: Android gemgemgen 프로젝트에서 불필요한 빌드 부산물과 캐시를 제외하고 다른 AI 에이전트 분석에 최적화된 경량 ZIP 아카이브를 생성하는 PowerShell 스크립트

param (
    [string]$ProjectRoot = "",
    [string]$DestinationZip = "",
    [switch]$IncludeRootFolder = $false
)

$ErrorActionPreference = "Stop"

# 1. 프로젝트 루트 경로 자동 설정
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
$projectName = Split-Path $ProjectRoot -Leaf

# 2. 목적지 ZIP 파일 경로 자동 설정
if (-not $DestinationZip) {
    $parentDir = Split-Path -Parent $ProjectRoot
    $DestinationZip = Join-Path $parentDir "$projectName.zip"
}
$DestinationZip = [System.IO.Path]::GetFullPath($DestinationZip)

Write-Host "=================================================="
Write-Host "Project ZIP Archive Generator (for AI Agents)"
Write-Host "=================================================="
Write-Host "- Project Root : $ProjectRoot"
Write-Host "- Destination  : $DestinationZip"
Write-Host "- Wrap Root Dir: $($IncludeRootFolder.IsPresent)"
Write-Host ""

# 3. .NET 압축 어셈블리 로드
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

# 4. 필터링 규칙 정의
$excludeDirNames = @('.git', '.gradle', '.idea', '.kotlin', '.grok', 'build')
$excludeFileNames = @('local.properties')
$excludeExtensions = @('.iml', '.pyc', '.zip')

Write-Host "Scanning and filtering files..."

$allFiles = Get-ChildItem -Path $ProjectRoot -Recurse -File -Force

$filesToInclude = @($allFiles | Where-Object {
    $relPath = $_.FullName.Substring($ProjectRoot.Length).TrimStart('\', '/')
    $parts = $relPath.Split([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    
    # 4-1. 제외 디렉터리 검사
    foreach ($part in $parts) {
        if ($excludeDirNames -contains $part) {
            return $false
        }
    }
    
    # 4-2. 서브모듈의 build 폴더 제외 방어
    if ($relPath -match "(^|[/\\])build([/\\]|$)") {
        return $false
    }

    # 4-3. 제외 파일명 및 확장자 검사
    if ($excludeFileNames -contains $_.Name) {
        return $false
    }
    if ($excludeExtensions -contains $_.Extension) {
        return $false
    }

    # 4-4. 프로젝트 루트에 직접 위치한 임시 xml 덤프 파일 제외 (res/xml 등 하위 리소스는 보존)
    if ($parts.Count -eq 1 -and $_.Extension -eq '.xml') {
        return $false
    }

    return $true
})

$rawTotalSize = ($filesToInclude | Measure-Object -Property Length -Sum).Sum
$rawSizeMB = [math]::Round($rawTotalSize / 1MB, 2)
Write-Host "- Selected Files : $($filesToInclude.Count)"
Write-Host "- Raw Total Size : $rawSizeMB MB"
Write-Host ""

# 5. 기존 대상 파일 제거
if (Test-Path $DestinationZip) {
    Remove-Item -Path $DestinationZip -Force
}

# 6. ZIP 아카이브 생성
Write-Host "Creating ZIP archive (standard '/' entries)..."

$zipStream = [System.IO.File]::Open($DestinationZip, [System.IO.FileMode]::Create)
$zipArchive = New-Object System.IO.Compression.ZipArchive($zipStream, [System.IO.Compression.ZipArchiveMode]::Create)

try {
    foreach ($file in $filesToInclude) {
        $relPath = $file.FullName.Substring($ProjectRoot.Length).TrimStart('\', '/')
        $normalizedRelPath = $relPath -replace '\\', '/'
        
        $entryPath = if ($IncludeRootFolder) {
            "$projectName/$normalizedRelPath"
        } else {
            $normalizedRelPath
        }

        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zipArchive,
            $file.FullName,
            $entryPath,
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
}
finally {
    $zipArchive.Dispose()
    $zipStream.Dispose()
}

# 7. 무결성 및 크기 검증
$finalZip = Get-Item $DestinationZip
$compressedSizeMB = [math]::Round($finalZip.Length / 1MB, 2)
$savingRate = if ($rawTotalSize -gt 0) {
    [math]::Round(((1 - ($finalZip.Length / $rawTotalSize)) * 100), 1)
} else {
    0
}

Write-Host "=================================================="
Write-Host "ZIP Archive Created Successfully!"
Write-Host "=================================================="
Write-Host "- File Path     : $($finalZip.FullName)"
Write-Host "- Zip Size      : $compressedSizeMB MB ($($finalZip.Length) bytes)"
Write-Host "- Entry Count   : $($filesToInclude.Count)"
Write-Host "- Size Reduced  : $savingRate %"
Write-Host ""

# 상위 폴더별 분포 출력
$filesToInclude | Group-Object {
    $rel = $_.FullName.Substring($ProjectRoot.Length).TrimStart('\', '/')
    $rel.Split([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)[0]
} | Select-Object Name, Count, @{Name="SizeMB"; Expression={[math]::Round(($_.Group | Measure-Object -Property Length -Sum).Sum / 1MB, 2)}} | Format-Table -AutoSize