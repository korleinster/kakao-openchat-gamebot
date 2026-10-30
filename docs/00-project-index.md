리니지2M 오픈채팅 일정 알림과 미니게임 프로젝트. 최종 갱신: <date start="2026-10-30" time-zone="Asia/Seoul" />.

현재 단계: Android 시험 앱 0.2.0-test의 다중 방·역할 분리 구현·폰 설치 완료. 단위 검사 29개와 폰의 독립 DB·설정 검사 11개 통과. 기존 연결·실행 설정 보존 확인. 새 화면·두 방 실제 응답·역할 밖 명령 차단 확인 완료. 0.2.0-test 예약 알림 재확인과 운영 일정·장시간 안정성 검증은 남아 있다.

신규 기능: 여러 방의 독립 연결·방별 역할·일정·전송 기록 분리. 실제 방별 역할은 미정이며 앱에서 초기 역할을 선택한다. 상세 범위·검증 현황은 11 문서에서 관리한다.

## 핵심 요구사항

- 월드보스·정기 콘텐츠: 시작 5분 전 안내.
- 매일 아침 07:00 Asia/Seoul: 당일 일정 한 번에 안내.
- 기간제 컬렉션: 종류별 2개월·4개월 등 공통 갱신 주기. 실제 자료는 사용자 제공 대기.
- 가벼운 뽑기·주사위, 캐치마인드는 참여·그림 전송 방식 추가 검토.
- Galaxy S20+ / Android 13 / One UI 5.1 / 카카오톡 26.9.1 실기기 확인. USB 테더링과 ADB 동시 연결·화면 조회·스크롤 입력 확인.

## 설계 기준

MIT 코드의 필요한 부분만 선별 활용하는 독립 앱을 준비한다. 방 식별이 모호하면 발송하지 않고, 앱 재시작 후 유효한 답장 정보를 다시 확보한다. 전송 요청 수락과 실제 카카오톡 도착을 구분하고, 결과 불명인 항목을 자동 중복 발송하지 않는다.

## 문서 안내

- <mention url="https://app.notion.com/p/408e60ccff0c81bfab2cf4ffcb0510f4?pvs=204">01 기획 개요와 요구사항</mention>
- <mention url="https://app.notion.com/p/408e60ccff0c81278567c882188341da?pvs=204">02 일정 알림 상세 기획</mention>
- <mention url="https://app.notion.com/p/408e60ccff0c81368c91d68f7e3cf1b4?pvs=204">03 카카오톡 연결·전송·복구 설계</mention>
- <mention url="https://app.notion.com/p/408e60ccff0c81c9bfebe9bb15fc5db9?pvs=204">04 아키텍처·데이터·기존 봇 연동</mention>
- <mention url="https://app.notion.com/p/408e60ccff0c81d58b0cfea22939db0c?pvs=204">05 미니게임 기획</mention>
- <mention url="https://app.notion.com/p/408e60ccff0c815a81abd5c84ec7add9?pvs=204">06 라이선스·원본 코드 관리</mention>
- <mention url="https://app.notion.com/p/408e60ccff0c81ce9e49f2cf4c5a7018?pvs=204">07 개발 계획·실기기 검증</mention>
- <mention url="https://app.notion.com/p/408e60ccff0c81d19d00ea5664cddef3?pvs=204">08 결정 사항·미정 항목·변경 이력</mention>
- <mention url="https://app.notion.com/p/408e60ccff0c81349e76c033c272268b?pvs=204">09 Android 원격 작업 준비·연결 기록</mention>
- <mention url="https://app.notion.com/p/408e60ccff0c8198a94cd84f56f63b14">10 구현 현황·테스트 기록</mention>

- <mention url="https://www.notion.so/409e60ccff0c81b49bdfc37c40c92eba">11 다중 방·역할 분리 기획 및 설계</mention>

## 다음 작업

기본 개발 도구·1:1 및 그룹방 실제 답장·30초 예약 알림·실시간 상태 표시 확인 완료. 다음은 실제 일정 자료 등록, 장시간 절전·복구 검증, 기존 봇 읽기 연동, 오락 확장이다.

폰의 최초 디버깅 허용은 사용자 승인을 완료했고 ADB 연결·화면 캡처·스크롤 조작을 확인했다. 테더링에 영향을 줄 수 있는 USB 모드·재부팅 시험은 영향 안내와 허가 후 수행한다. 독립 GitHub 저장소는 [korleinster/kakao-openchat-gamebot](https://github.com/korleinster/kakao-openchat-gamebot)이며 사용자가 공개 업로드를 선택했다. MIT를 유지하고 원작자·원본 소스를 별도 고지에 표시한다.
