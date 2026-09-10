---
name: update-accessibility-nodes
description: >-
  ChatGPT 또는 Gemini 등 외부 AI 앱이 업데이트되어 '접근성' 노드 식별자가 변경되었을 때,
  실제 Android 기기(ADB)를 통해 UI 계층을 실측(uiautomator dump)하고 접근성 노드 탐색 로직과
  Fallback 체계를 안전하게 갱신·검증하는 접근성 노드 식별자 최신화 워크플로우 스킬.
---

# 안드로이드 AI 앱 '접근성' 노드 식별자 최신화 스킬 (Accessibility Node Updater)

외부 AI 앱(ChatGPT, Gemini 등)은 앱 버전이 업데이트될 때마다 메뉴, 새 채팅, 입력창, 메시지 전송 버튼의 `contentDescription`, `text`, 레이아웃 계층 구조가 변경될 수 있습니다.  
이 스킬은 추측에 의존하지 않고 **실제 연결된 Android 기기(ADB)의 UI 노드를 단계별로 실측(dump)하여, 접근성 노드 파인더 코드를 안전하게 갱신하고 검증하는 표준 절차**를 제공합니다.

---

## 0. 사용 시점 및 기본 원칙

- **사용 시점**:
  - ChatGPT 또는 Gemini 자동화 실행 시 *"새 채팅 버튼을 찾지 못했습니다"*, *"메뉴를 찾지 못했습니다"* 등의 10초 타임아웃 오류가 발생할 때
  - 대상 앱의 버전이 업데이트되어 접근성 노드 식별자 갱신이 필요할 때
- **시니어 파트너 원칙**:
  - 임의로 코드를 추측해서 바꾸지 않는다.
  - 반드시 실제 기기에서 `uiautomator dump`를 떠서 최신 노드 속성을 확인한다.
  - 단일 문자열 완전 일치(`equals`) 대신 **다중 Fallback 후보군(키워드 리스트) 및 `.trim()` 방어 로직**을 적용한다.
  - 작업 완료 후 임시 XML 파일은 즉시 정리한다.

---

## 1. 대상 앱 패키지 및 기본 정보

| 대상 앱 | 공식 패키지명 | 핵심 메인 액티비티 |
| :--- | :--- | :--- |
| **ChatGPT** | `com.openai.chatgpt` | `com.openai.chatgpt.MainActivity` |
| **Gemini** | `com.google.android.apps.bard` | `com.google.android.apps.bard.ui.activity.MainActivity` |

---

## 2. 단계별 실행 절차 (Runbook)

### 1단계: 환경 및 기기 상태 점검

1. **ADB 경로 및 연결 기기 확인**:
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
   ```
2. **대상 앱의 설치된 버전 확인**:
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> shell dumpsys package <PACKAGE_NAME> | Select-String "versionName"
   ```
3. **사용자 화면 방해 방지**:
   - 현재 사용자가 화면을 보고 있는지 포커스 윈도우 확인:
     ```powershell
     & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> shell dumpsys window | Select-String "mCurrentFocus"
     ```
   - 사용자에게 ChatGPT/Gemini 화면을 띄워 노드를 덤프할 것임을 안내하거나 동의를 구함.

---

### 2단계: UI 계층 구조 실측 (단계별 XML 덤프)

아래 순서대로 각 화면 상태를 만들고 `uiautomator dump`를 수집합니다.

1. **앱 실행 및 메인 화면 덤프**:
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> shell am start -n <PACKAGE_NAME>/<MAIN_ACTIVITY>
   Start-Sleep -Seconds 2
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> shell uiautomator dump /data/local/tmp/dump_main.xml
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> pull /data/local/tmp/dump_main.xml ./dump_main.xml
   ```

2. **메뉴(사이드바) 열림 상태 덤프**:
   - 메뉴 버튼 클릭 후 사이드바 덤프 수집:
     ```powershell
     # 메뉴 좌표 탭 (또는 UI 상 메뉴 노드 중심 클릭)
     & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> shell uiautomator dump /data/local/tmp/dump_sidebar.xml
     & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> pull /data/local/tmp/dump_sidebar.xml ./dump_sidebar.xml
     ```

3. **새 대화(입력창 비어있는 상태) 덤프**:
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> shell uiautomator dump /data/local/tmp/dump_newchat.xml
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> pull /data/local/tmp/dump_newchat.xml ./dump_newchat.xml
   ```

4. **텍스트 입력 후(보내기 버튼 활성화 상태) 덤프**:
   ```powershell
   # 입력창 클릭 후 테스트 텍스트 입력
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> shell input text "test"
   Start-Sleep -Milliseconds 500
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> shell uiautomator dump /data/local/tmp/dump_typing.xml
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> pull /data/local/tmp/dump_typing.xml ./dump_typing.xml
   ```

5. **메시지 전송 후(반복 새 채팅 버튼 노출 상태) 덤프**:
   ```powershell
   # 전송 버튼 클릭 후 덤프
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> shell uiautomator dump /data/local/tmp/dump_sent.xml
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <DEVICE_ID> pull /data/local/tmp/dump_sent.xml ./dump_sent.xml
   ```

---

### 3단계: XML 파싱 및 변경 대조표 작성

PowerShell을 활용해 추출한 XML에서 텍스트와 description을 한눈에 조회합니다:

```powershell
[xml]$xml = Get-Content ./dump_sidebar.xml -Encoding UTF8
$xml.SelectNodes("//node[@text != '' or @content-desc != '']") | 
    ForEach-Object { "$($_.class) | text: '$($_.text)' | desc: '$($_.GetAttribute('content-desc'))' | bounds: $($_.bounds)" }
```

- **핵심 점검 체크리스트**:
  - [ ] 메뉴 버튼: `desc`가 여전히 `"메뉴"`인가? (`"사이드바 열기"`, `"탐색 창 열기"` 등으로 변경되었는가?)
  - [ ] 첫 채팅 진입: 사이드바 상단에 `"새 채팅"`이 있는가? 하단 텍스트가 `"채팅"`인가 `"Chat"`인가?
  - [ ] 반복 새 채팅: 전송 후 우측 상단 노드의 `desc`가 `"새 채팅"`인가?
  - [ ] 입력창: `className`이 `EditText`인가? `isEditable`인가?
  - [ ] 전송 버튼: 텍스트 입력 시 `desc`가 `"메시지 보내기"`인가? `"전송"`인가?
  - [ ] 팝업/공지: 화면을 가리는 다이얼로그의 닫기 버튼 식별자가 있는가?

---

### 4단계: 접근성 코드(`NodeFinder`) 수정 규칙

1. **다중 Fallback 상수 정의**:
   ```kotlin
   internal companion object {
       val NEW_CHAT_DESCRIPTIONS = listOf("새 채팅", "새 대화", "새로운 채팅", "New chat")
       val INITIAL_CHAT_TEXTS = listOf("채팅", "Chat", "대화")
       val MENU_DESCRIPTIONS = listOf("메뉴", "사이드바 열기", "탐색 창 열기", "Menu", "Open navigation drawer")
       val SEND_DESCRIPTIONS = listOf("메시지 보내기", "보내기", "전송", "Send message", "Send")
       val SEARCH_DESCRIPTIONS = listOf("검색", "Search")
       val SETTINGS_DESCRIPTIONS = listOf("계정 설정", "설정", "Settings", "Account settings")
   }
   ```
2. **트림(`.trim()`) 및 대소문자 무시 비교**:
   접근성 노드 텍스트에 줄바꿈이나 공백이 들어간 경우를 대비해 반드시 `.trim()`을 적용하여 탐색.
3. **사양 문서 최신화**:
   `docs/chatgptaddmvp.md` 등 관련 문서의 확인된 버전 및 식별값 표를 최신으로 갱신.

---

### 5단계: 검증 및 정리

1. **국소 단위 테스트 실행**:
   ```powershell
   .\gradlew testDebugUnitTest --tests "com.example.gemgemgen.ChatGptAccessibilityNodeFinderTest"
   .\gradlew testDebugUnitTest --tests "com.example.gemgemgen.RunAutomationUseCaseTest"
   ```
2. **기기 설치 및 동작 확인**:
   ```powershell
   .\gradlew installDebug
   ```
3. **임시 XML 덤프 파일 정리**:
   ```powershell
   Remove-Item -Path @("dump_main.xml", "dump_sidebar.xml", "dump_newchat.xml", "dump_typing.xml", "dump_sent.xml") -ErrorAction SilentlyContinue
   ```
4. **전체 파일 증감 내역 표 작성 및 사용자 보고**:
   - 프로덕션 코드와 테스트 코드를 분리한 증감표와 함께, 사용자가 앱에서 직접 테스트할 수 있는 확인 체크리스트를 제공.
