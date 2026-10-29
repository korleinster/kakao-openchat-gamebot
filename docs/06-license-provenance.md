검토일: <date start="2026-10-29" time-zone="Asia/Seoul" />
문서 상태: 공개 소스·라이선스 검토 기록 v0.1. 현재 준비물에는 원본 실행 코드를 복사하지 않았고 원본 MIT 라이선스 사본과 검토 기준을 보존한다.

## 1. 선택한 개발 방향

bssm-oss/kakao-talk-auto-bot의 MIT로 허용된 필요한 코드만 선별 재사용하는 독립 앱. 검토 기준 커밋: a10b27b46984b8b85712bf84bf452596f607f5c7. 이 커밋의 마지막 변경 시각은 2026년 6월 8일이다. 최근 활동이 있다는 이유만으로 계속 유지보수되거나 최신 카카오톡에서 작동한다고 확정하지 않는다.

lee775/KakaoBot-Android는 README에 개인 사용 목적이라고만 쓰여 있고 별도 재사용 라이선스가 확인되지 않는다. 소스가 공개돼 있어도 독립 파생 개발 허가로 간주하지 않는다. 별도 허락 없이 이 후보의 실행 코드를 가져오지 않는다.

## 2. MIT 사용 조건과 독립 저장소

MIT는 사용·복사·수정·병합·출판·배포·재라이선스·판매를 허용한다. 복사본 또는 상당 부분을 포함한 결과물에 원저작권 표시와 허가문을 보존해야 한다. 공개 저장소뿐 아니라 별도 비공개 개발도 이 조건을 따른다.

- GitHub의 Fork 버튼을 반드시 사용하거나 원본 Git 이력을 유지해야 하는 의무는 MIT에 없다.
- 새 저장소·새 앱 이름·새 applicationId·새 서명키로 독립 관리 가능.
- 원본 저작권 표시: Copyright (c) 2026 kakao-talk-auto-bot contributors.
- 자체 신규 코드의 저작권 표시를 추가할 수 있지만 원본 기여자의 표시를 지우거나 전부 자체 창작으로 표시하지 않는다.
- 자체 코드 공개를 강제하는 copyleft 의무는 MIT 자체에는 없다.
- 저장소와 배포 APK의 오픈소스 고지 화면에 실제 포함한 코드·라이선스를 반영한다. README의 원본 링크는 고지문 보존을 대체하지 않는다.

## 3. 선별 재사용 기록

| 대상 | 검토 기준 | 현재 상태 |
| --- | --- | --- |
| NotificationListener.kt | 알림 수신·파싱 | 소스 검토 완료, 복사·수정 전 |
| SessionManager.kt | 방별 메모리 Action 캐시 | 소스 검토 완료, 식별·복구 설계 보완 필요 |
| SessionReplier.kt | RemoteInput 전송·상태 | 소스 검토 완료, 결과 의미·재시도 정책 변경 필요 |
| 로컬 AI 및 대화 저장 | 앱에 결합된 별도 기능 | 1차 미채택 제안 |
| 앱 아이콘·브랜드 | 원본 자산 | 그대로 사용하지 않고 별도 제작·확인 |

파일을 가져올 때마다 원본 URL·커밋·파일 경로·원본 해시·변경 목적을 provenance에 추가한다. 현재 upstream.lock.json의 copied_files는 비어 있다.

## 4. 외부 라이브러리

원본 Gradle/CMake에는 AndroidX, Material, Kotlin coroutines, org.json, LiteRT-LM, llama.cpp 등이 선언돼 있다. 원본 전체를 복제하면 AI 실행 도구까지 의존성이 따라오므로 필요한 통신 부분만 옮기는 쪽을 제안한다.

라이브러리나 모델은 최상위 MIT로 일괄 덮어쓰지 않는다. 실제 빌드에서 해결된 직접·전이 의존성을 목록화하고 버전별 라이선스·NOTICE를 배포 전 검사한다. LiteRT-LM과 현재 참조 Gemma 4 E2B 변환 모델은 Apache-2.0 표기를 확인했지만, 다른 버전·대체 모델까지 동일하다고 가정하지 않는다. Gemma 구세대 약관을 Gemma 4에 그대로 적용하지 않는다.

APK 배포 전: 포함 라이선스 원문·필수 NOTICE 유지, 수정 표시가 필요한 파일은 표시, 별도 자산·상표 권리 확인. MIT 코드 사용 허가와 카카오톡 서비스의 비공식 자동화 허용 여부는 별개의 검토 범위다.

## 5. GitHub 준비 상태

연결 계정은 korleinster로 확인했다. 로컬 개발 준비 폴더만 구성하며 원격 저장소 생성·공개·푸시를 완료했다고 표시하지 않는다. 이름·공개 범위가 정해지면 실제 원격 저장소를 연결한다. README는 영어로 유지한다.

## 6. 근거

- [기준 커밋의 원본 MIT](https://github.com/bssm-oss/kakao-talk-auto-bot/blob/a10b27b46984b8b85712bf84bf452596f607f5c7/LICENSE)
- [원본 빌드 의존성](https://github.com/bssm-oss/kakao-talk-auto-bot/blob/a10b27b46984b8b85712bf84bf452596f607f5c7/app/build.gradle.kts)
- [원본 native build](https://github.com/bssm-oss/kakao-talk-auto-bot/blob/a10b27b46984b8b85712bf84bf452596f607f5c7/app/src/main/cpp/CMakeLists.txt)
- [MIT 공식 문구](https://opensource.org/license/mit)
- [라이선스가 없는 공개 저장소의 취급](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)
- [제외 후보 README](https://github.com/lee775/KakaoBot-Android/blob/3927541cb42d18acc9f4468f6daaf107d0213026/README.md)
- [LiteRT-LM LICENSE](https://github.com/google-ai-edge/LiteRT-LM/blob/main/LICENSE)
- [실제 참조 Gemma 4 모델 카드](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
