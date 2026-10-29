리니지2M 오픈채팅 일정 알림과 미니게임 프로젝트. 최종 갱신: <date start="2026-10-29" time-zone="Asia/Seoul" />.

현재 단계: 개발 준비·설계 초안 v0.1. APK 구현·빌드·실기기 전송·운영 적용은 아직 완료되지 않았다.

## 핵심 요구사항

- 월드보스·정기 콘텐츠: 시작 5분 전 안내.
- 매일 아침 07:00 Asia/Seoul: 당일 일정 한 번에 안내.
- 기간제 컬렉션: 종류별 2개월·4개월 등 공통 갱신 주기. 실제 자료는 사용자 제공 대기.
- 가벼운 뽑기·주사위, 캐치마인드는 참여·그림 전송 방식 추가 검토.
- Galaxy S20+ / Android 13 / One UI 5.1. 카카오톡 정확한 버전은 미검증. 현재 USB 테더링 용도.

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

## 다음 작업

개발 도구·폰 디버깅 연결 준비 → 테스트 오픈채팅에서 텍스트 전송 검증 → 방 구분·연결 복구 → 영속 일정과 5분 전·아침 알림 → 컬렉션·오락 확장.

폰의 최초 디버깅 허용은 사용자 조작이 필요하다. 테더링에 영향을 줄 수 있는 USB 모드·재부팅 시험은 영향 안내와 허가 후 수행한다. 연결된 GitHub 계정은 korleinster이며 원격 저장소는 아직 만들지 않았다.
