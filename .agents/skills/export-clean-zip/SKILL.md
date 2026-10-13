---
name: export-clean-zip
description: >-
  다른 AI 에이전트(Claude, ChatGPT, Cursor 등)에 프로젝트 코드를 첨부·공유할 때,
  불필요한 빌드 산출물(build, .gradle), IDE 설정, 캐시, 개인정보를 철저히 제외하고
  표준 슬래시(/) 및 Flat 구조로 초경량 ZIP 아카이브를 자동 생성·검증하는 스킬.
---

# 프로젝트 경량 ZIP 압축 스킬 (AI 에이전트 공유 최적화)

다른 AI 에이전트(Claude Projects, ChatGPT Code Interpreter, Cursor 등)에 프로젝트 코드를 전달할 때 용량 초과를 방지하고, 분석 효율을 극대화하기 위해 **빌드 산출물과 캐시를 제외한 핵심 소스 및 가이드 문서만 초경량 ZIP으로 패키징**하는 전용 워크플로우 스킬입니다.

---

## 0. 사용 시점 및 트리거 조건

- **사용 시점**:
  - "이 앱 파일을 zip으로 만들어줘"
  - "다른 에이전트에 올릴 zip 압축해줘"
  - "프로젝트 코드 zip으로 묶어줘"
  - `$export-clean-zip` 명령 호출 시

---

## 1. AI 에이전트 분석 최적화 4대 원칙

1. **Flat 계층 구조 (경로 깊이 최소화)**
   - 최상위에 불필요한 감싸는 폴더(`gemgemgen/`) 없이, 압축 해제 즉시 `app/`, `build.gradle.kts`, `AGENTS.md` 등이 바로 인식되도록 구성합니다.
   - AI 에이전트가 `cd` 명령이나 추가 경로 뎁스 탐색 없이 즉시 원래 프로젝트 구조와 동일하게 코드를 탐색할 수 있습니다.
2. **ZIP 표준 슬래시(`/`) 구분자 강제**
   - Windows의 백슬래시(`\`) 대신 표준 슬래시(`/`)를 강제하여 Linux/Mac 기반 컨테이너 및 웹 기반 AI 파서에서 디렉토리 계층이 깨지지 않도록 합니다.
3. **빌드 산출물 및 임시 캐시 98% 다이어트**
   - 수백 MB를 차지하는 `build/`, `app/build/`, `.gradle/`, `.git/` 바이너리를 제외하여 수 MB(4~5MB) 수준으로 경량화합니다.
4. **AI 컨텍스트 및 규칙 문서 완전 보존**
   - 소스 코드(`app/src/`)뿐만 아니라 에이전트가 프로젝트 아키텍처와 규칙을 파악할 수 있도록 `AGENTS.md`, `docs/`, `.agents/`, `design-system/`을 빠짐없이 포함합니다.

---

## 2. 파일 필터링 규칙 (Exclusion & Inclusion)

| 구분 | 대상 경로 / 파일 | 설명 및 사유 |
| :--- | :--- | :--- |
| **제외** | `build/`, `app/build/`, `*/build/` | 컴파일된 .dex, .class, APK 등 빌드 산출물 (약 250MB+) |
| **제외** | `.gradle/`, `.kotlin/`, `.grok/` | Gradle 증분 빌드 캐시 및 도구 임시 파일 (약 20MB+) |
| **제외** | `.git/` | Git 로컬 바이너리 및 히스토리 (컨텍스트 낭비 방지) |
| **제외** | `.idea/`, `local.properties`, `*.iml` | Android Studio 로컬 환경 및 개인 SDK 경로/토큰 |
| **제외** | 루트의 `*.xml` (`dump.xml`, `local_dump*.xml`) | 임시 UI Automator 덤프 파일 |
| **제외** | `*.zip`, `.DS_Store`, `*.pyc` | 기존 압축 파일 및 임시 파일 |
| **포함** | `app/src/` | 프로덕션 소스, 리소스(res/xml 포함), 단위 테스트 코드 일체 |
| **포함** | 루트 및 모듈 빌드 스크립트 | `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `app/build.gradle.kts` |
| **포함** | Gradle 래퍼 (`gradle/`, `gradlew`, `gradlew.bat`) | 프로젝트 빌드 환경 정의 및 버전 명세 |
| **포함** | 에이전트 지침 및 설계 문서 | `AGENTS.md`, `.agents/`, `docs/`, `design-system/`, `README.md`, `프롬프트 자동화 과정.md` |
| **포함** | 버전 관리 설정 | `.gitignore` |

---

## 3. 실행 방법 (Runbook)

### 표준 실행 (AI 최적화 Flat 구조, 권장)

프로젝트 루트에서 전용 PowerShell 자동화 스크립트를 실행합니다.

```powershell
powershell.exe -ExecutionPolicy Bypass -File ".agents\skills\export-clean-zip\scripts\export_clean_zip.ps1"
```

- **기본 출력 위치**: `C:\Users\joajo\AndroidStudioProjects\<프로젝트명>.zip` (프로젝트 바로 바깥 상위 폴더)

### 옵션별 실행 예시

1. **루트 폴더로 감싸서 압축하고자 할 때** (`-IncludeRootFolder`):
   ```powershell
   powershell.exe -ExecutionPolicy Bypass -File ".agents\skills\export-clean-zip\scripts\export_clean_zip.ps1" -IncludeRootFolder
   ```
2. **저장 위치를 변경하고자 할 때** (`-DestinationZip`):
   ```powershell
   powershell.exe -ExecutionPolicy Bypass -File ".agents\skills\export-clean-zip\scripts\export_clean_zip.ps1" -DestinationZip "C:\Users\joajo\Desktop\my_export.zip"
   ```

---

## 4. 사후 검증 체크리스트

스크립트 실행 후 에이전트는 다음 사항을 사용자에게 보고합니다.

- [ ] 생성된 ZIP 파일 경로 및 최종 용량 (MB 단위)
- [ ] 제외 대상(`.git`, `build`, `.gradle`, `local.properties` 등)이 포함되지 않았는지 검증 결과
- [ ] 총 포함된 엔트리 수 및 주요 영역별(app, docs, .agents 등) 파일 분포 요약
- [ ] 사용자가 즉시 파일을 클릭하여 열 수 있는 링크 제공
