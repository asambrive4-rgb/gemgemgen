# Dumps & Screenshots (`docs/dumps/`)

이 디렉토리는 접근성 노드 분석(`uiautomator dump`) 결과 파일(XML) 및 UI 화면 확인용 스크린샷(PNG) 파일들을 보관하는 디렉토리입니다.

## 보관 파일 유형
- **XML 덤프 (`dump_*.xml`)**: Android 디바이스에서 실행 중인 Gemini 등의 UI 계층 구조 및 접근성 노드(`resource-id`, `content-desc`, `text`, `bounds` 등) 분석용 덤프 파일입니다.
- **스크린샷 (`screen*.png`)**: 특정 동작 시점(입력창 활성화, 사이드바 열기, 전송 후 등)의 화면 상태를 시각적으로 확인하기 위한 캡처 이미지 파일입니다.

## 파일 목록
- `dump_gemini_after_send.xml`: 메시지 전송 후 화면 UI 덤프
- `dump_gemini_curr.xml`: 현재 Gemini 화면 UI 덤프
- `dump_gemini_fast150.xml`: Fast 1.5 모드 화면 UI 덤프
- `dump_gemini_main.xml`: 메인 화면 UI 덤프
- `dump_gemini_model.xml`: 모델 선택 UI 덤프
- `dump_gemini_new_chat.xml`: 새 채팅 화면 UI 덤프
- `dump_gemini_options.xml`: 옵션 메뉴 UI 덤프
- `dump_gemini_sb.xml`: 사이드바 UI 덤프
- `dump_gemini_sb_scroll.xml`: 사이드바 스크롤 후 UI 덤프
- `dump_gemini_sidebar.xml`: 사이드바 UI 덤프
- `dump_gemini_tablet.xml`: 태블릿 모드 UI 덤프
- `dump_gemini_typing.xml`: 텍스트 입력 중 UI 덤프
- `dump_temp_chat.xml`: 임시 채팅 화면 UI 덤프
- `screen.png`: 기본 캡처 스크린샷
- `screen_after_close_temp.png`: 임시 창 닫기 후 스크린샷
- `screen_after_tap.png`: 탭 동작 후 스크린샷
- `screen_in_chat.png`: 채팅 화면 스크린샷
- `screen_tap_pencil_in_chat.png`: 채팅 화면 연필(편집) 아이콘 탭 후 스크린샷
