검토일: <date start="2026-10-30" time-zone="Asia/Seoul" />
문서 상태: 출시 준비 기준·제출 자료 초안. Play Console 제출 또는 승인 완료 문서가 아니다.

## 1. 제출 기준과 구현 대응

| 항목 | 확인한 기준·대응 | 현재 구분 |
| --- | --- | --- |
| Android 대상 | 신규 앱 제출은 Android 16/API 36 이상. compile/target 36, AGP 8.11.1·Gradle 8.13·Kotlin 2.2.21·JDK 17로 전환 | 빌드·API 36 시험 검사 완료 |
| 배포 산출물 | release AAB, 디버깅 비활성, 업로드 키는 로컬 signing.properties로만 지정 | 서명 정보 없으면 미서명 번들. 사용자 업로드 키·Play App Signing 절차 별도 |
| 알림 접근 | 카카오톡 알림 내용·식별 정보를 읽는 목적·백그라운드 처리·저장·전송 대상을 앱에서 설명한 뒤 동의 | 안내·동의·철회 게이트 구현·자동 검사 통과 |
| 정확한 알림 | SCHEDULE_EXACT_ALARM을 사용자가 설정에서 허용. USE_EXACT_ALARM은 선언하지 않음 | 알림 기능에서 필요한 시점에 요청 |
| 처리방침 | 앱 안과 공개 URL에서 접근·저장·전송·보관·삭제·문의 경로 안내 | 앱 내 전문과 공개 GitHub 처리방침 준비·공개 주소 읽기 확인 |
| 브랜드·기능 설명 | 앱 이름 ‘채팅봇’, 자체 아이콘, 실제 구현한 명령·알림만 설명 | 카카오의 공식·제휴 앱이라는 표현이나 로고 사용 안 함 |
| 테스트 | 최신 OS와 실제 폰의 동의·권한·복구·레이아웃·발송 검증 | 자동 검사·화면 점검 완료, 새 버전 실제 답장·장기 복구 시험 남음 |

공식 근거: [대상 API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en), [AGP 8.11 호환](https://developer.android.com/build/releases/agp-8-11-0-release-notes), [Kotlin 호환](https://kotlinlang.org/docs/gradle-configure-project.html), [사용자 데이터·공개 설명과 동의](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en), [정확한 알람 권한](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en-GB), [사칭 방지](https://support.google.com/googleplay/android-developer/answer/9888374?hl=en-GB).

## 2. 실제 데이터 흐름과 Data safety 준비

앱 자체에는 INTERNET 권한·광고·분석 SDK·외부 AI API·회원 계정이 없다. 카카오톡 알림의 식별 정보와 명령 텍스트는 기기 안에서 처리한다. 앱이 생성한 답장·예약 알림은 사용자가 연결하고 켠 대화로 카카오톡을 통해 전달된다. 뽑기 후보 등 사용자가 입력한 정보가 발송 내용에 포함될 수 있다. 외부 문의 링크는 브라우저/외부 서비스가 처리한다.

Data safety 폼의 기기 내 처리·사용자 주도 전송/공유 예외 정의에 맞춰 최종 배포본을 검토해야 한다. 로컬 처리라는 이유만으로 메신저 전송을 숨기거나 ‘어떠한 정보도 전송하지 않음’이라고 기재하지 않는다. 실제 SDK·권한·네트워크 경로와 Console 답변을 대조한다. [Data safety 작성 기준](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).

## 3. 소유자 계정에서 마쳐야 할 일

- Play Console 개발자 계정·신원/연락처 확인과 공개 지원 이메일 확정. 사용자는 현재 앱 이름만 제공했다.
- 업로드 키 지정·보관, Play App Signing 선택, 서명한 최종 AAB 업로드.
- 앱 액세스 안내, Data safety, 콘텐츠 등급·대상 연령·광고 여부·스토어 카테고리 입력.
- 실제 지원 이메일·처리방침 URL·앱 내용과 일치하는 스크린샷·아이콘/소개 이미지 등록.
- 해당하는 신규 개인 개발자 계정은 12명이 연속 14일 참여하는 비공개 테스트 후 프로덕션 접근 신청. 계정 유형·생성일을 확인하기 전 적용 여부를 확정하지 않는다.
- Android 16 기기·장기 절전·복구·다중 방 실제 발송 범위의 미통과 항목 해소 후 제출.

[개인 개발자 계정 테스트 요건](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

## 4. 리뷰어 접근 안내

앱 자체 로그인은 없다. 방 설정·말투 미리보기·알림 편집·개인정보 기능은 앱 안에서 확인할 수 있다. 실제 송수신에는 카카오톡이 설치된 폰, 사용자가 참여한 시험 대화, 메시지를 보내는 다른 계정, 알림 접근과 정확한 알람 권한이 필요하다. 등록한 시험 대화의 새 알림으로 연결한다.

심사용 실제 시험 환경·재현 영상·필요 계정 제공 방식은 소유자가 준비해야 한다. 개발자 개인의 실제 카카오톡 계정·대화·개인정보를 자동으로 공유하지 않는다. 리뷰어에게만 다른 기능을 보여주는 숨김 모드를 만들지 않는다.

카카오톡 연동은 Android 알림 답장에 의존하며, 카카오 공식 API 제공·제휴·서비스 허가를 의미하지 않는다. 메신저 업데이트와 운영 정책은 별도 검토 대상이다. [카카오톡 운영 정책](https://talksafety.kakao.com/policy).

## 5. 산출물과 검증 상태

- 앱 기능·디자인: 12 문서, 소스와 실제 결과를 구분해 관리.
- 스토어 한국어 문안: distribution/ko-KR/.
- 공개 처리방침: docs/privacy-policy.md. 앱 전문과 의미를 맞춘다.
- 빌드 산출물: app/build/outputs/apk/debug/app-debug.apk, app/build/outputs/bundle/release/app-release.aab.
- 실제 제출·계정 테스트·Google 심사 승인: 미실행. 서명/연락처/접근 자료/필수 시험이 남으면 ‘출시 가능 확인 완료’로 표시하지 않는다.

## 6. 제출 준비 산출물 검증

- 0.3.0 / versionCode 4 / min 26 / target 36. release manifest에서 디버깅 비활성 확인.
- 단위 35개·폰 제품 19개·API 36 시험 제품 18개 통과, debug/release lint 오류 0건. 실제 새 버전 수신자 시험은 대기 중이며 승인·출시 완료로 해석하지 않는다.
- APK·AAB ZIP 무결성 확인. AAB에 서명 인증서와 네이티브 .so가 없음을 확인했다. 미서명 AAB이므로 현재 파일 그대로 Play 업로드할 수는 없다.
- 자체 아이콘 512×512 RGBA PNG, 소개 이미지 1024×500 RGB PNG, 실제 앱 화면 JPEG 4장 1080×1920을 준비했다. 개인 대화 대신 시험 환경의 예시 데이터만 사용했다. 다크 모드/큰 글씨 화면 1장이 포함된다.
- 스토어 소개·스크린샷은 distribution/ 아래, 대체 텍스트·형식·촬영 조건은 distribution/README.md와 alt-text.json에서 관리한다. [공식 이미지 규격](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).
- debug APK SHA-256: `3a037f29cb1734f37a8f7be70f2ba18b88bd80b73d5656c45fbb0e01b73445f2`.
- 미서명 release AAB SHA-256: `8ad07d4ff342148fed3a9a5487e341bf1b2ab7ee2a188640a51a78d219a207d9`.

위 해시는 서명 전 로컬 산출물을 식별한다. 업로드 키로 서명한 최종 파일은 해시와 검증 기록을 다시 남긴다. 소유자 지원 이메일·서명·Console 입력·심사 접근 자료·해당 계정의 비공개 테스트가 남아 있다.

코드·빌드 검증 기준: [18a93dc](https://github.com/korleinster/kakao-openchat-gamebot/commit/18a93dc288cf9cecf68608df0830d767e436f6bd). 공개 저장소 업로드와 처리방침 파일 조회를 확인했다.
