검토일: <date start="2026-10-29" time-zone="Asia/Seoul" />
문서 상태: 기술 설계 초안 v0.1. 아래 모듈·테이블·인터페이스는 구현 전 제안이다.

## 1. 전체 구성

기존 보탐봇/수동 일정 → 읽기 전용 일정 입력 어댑터 → 로컬 일정 DB → 회차 계산 → 발송 대기 DB → Android 알람 → 방 확인 → 카카오톡 답장 어댑터.

채팅 명령은 알림 입력 → 허용된 방 검사 → 명령 해석 → 일정 조회/오락 처리 → 같은 전송 어댑터로 이동한다. 네트워크 동기화 장애가 명령 응답이나 로컬에 유효하게 저장된 일정 계산을 중단시키지 않게 분리한다.

## 2. 모듈 경계

| 모듈 | 책임 | 금지·의존 조건 |
| --- | --- | --- |
| domain | 한국 시각 회차·알림 시각·컬렉션 계산, 명령 규칙 | Android·네트워크에 의존하지 않는 순수 Kotlin 제안 |
| data | Room DB, 설정·발송 상태, migration | 세션 Action·개인 대화 원문을 영속 토큰으로 저장하지 않음 |
| transport | NotificationListener, 파서, 방 연결, RemoteInput | 카카오톡에서 얻은 답장 정보만 사용 |
| scheduling | 정확한 알람 등록·복원, 발송 선점·기한 판정 | WorkManager 주기를 정시 알림으로 사용하지 않음 |
| integration | 수동 JSON 입력, 추후 기존 보탐봇 읽기 어댑터 | 디스코드 토큰·서버 내부 경로를 폰에 노출하지 않음 |
| app | 설정·권한·상태·기록 화면 | 설정 변경은 로컬 앱에서만 허용하는 1차 안 |

단일 앱 패키지 안에 명확한 패키지 경계를 먼저 두고, 실제 규모가 커질 때 Gradle 멀티모듈로 분리한다. 불필요한 서버·AI 엔진을 초기 의존성으로 추가하지 않는다.

## 3. 영속 데이터 제안

| 엔터티 | 주요 필드 | 규칙 |
| --- | --- | --- |
| RoomBinding | id, display_name, package_name, profile_scope, identity_evidence, verification_state, generation | 운영 대상은 1개 방으로 시작. 표시 이름은 고유 키가 아님 |
| ScheduleDefinition | id, category, name, timezone, recurrence, enabled, revision, provenance | 원본 검증 범위와 데이터 유효기간 분리 |
| Occurrence | id, schedule_id, logical_cycle_key, starts_at, revision, status | 원본 변경에도 논리 회차 ID를 유지. 공식 회차 ID 우선 |
| CollectionDefinition | id, basis, anchor_at, interval_months, overrides, notice_offsets_days | 불완전한 입력은 disabled |
| Delivery | id, room_id, occurrence_id, notice_kind, part_id, due_at, expires_at, state, run_generation | 방·논리 회차·안내 종류·파트 고유 제약 |
| DeliveryAttempt | id, delivery_id, claim_time, invoke_time, result_code, latency | 요청 정상 반환과 실제 도착을 구분 |
| AppConfig | auto_send_enabled, morning_time, timezone, run_generation | 최초 OFF. 아침 시각은 사용자 확정 07:00 |
| GameSession | id, room_id, type, phase, participants, started_at, deadline | 재시작 뒤 실행 중 게임은 중단 상태로 복구 |

입력 실패를 0회·0건으로 대체하지 않는다. 오류·미등록·비활성·기한 만료를 별도 코드로 저장한다.

## 4. 일정 원본과 기존 디스코드 봇 연동

첫 시제품은 검증된 수동 JSON 입력으로 전송 경로를 독립 검증한다. 실제 월드보스와 정기 콘텐츠 데이터는 사용자 자료 또는 현재 운영 원본을 확인한 뒤 등록한다.

기존 봇의 로컬 문서에는 인증된 Unix-socket 웹 브리지가 존재한다. 안드로이드가 그 소켓에 직접 연결할 수는 없다. 추후 연동 시 서버 측 읽기 전용 어댑터와 인증된 HTTPS 경로가 필요한지 실제 운영 코드를 확인하고 별도 설계한다. 기존 운영 봇의 재시작·배포·포트 개방을 이번 준비 작업에 포함하지 않는다.

제안 계약: schema_version, source_id, source_revision, generated_at, valid_until, schedules, collections. 실제 일정의 출처와 last_verified_at도 항목별로 저장한다. 기존 회차 취소와 삭제를 표현할 수 있어야 한다.

- 스냅샷은 전체 검증 후 한 트랜잭션으로 교체. 일부 잘못된 항목 때문에 부분 적용하지 않는 1차 안.
- source_revision이 오래된 결과는 최신 데이터 위에 덮어쓰지 않는다. source_id가 다르면 서로의 revision을 비교하지 않는다.
- valid_until은 동기화 데이터 신선도다. 컬렉션의 게임 내 갱신일과 같은 필드로 사용하지 않는다.
- 네트워크 장애 때 마지막 정상 데이터가 유효 범위 안이면 사용. 범위 밖이면 해당 자동 알림은 중지하고 앱에 '일정 확인 필요' 표시.
- 서버 변경·점검·이벤트 취소는 예외 회차로 원본을 보정. 문서에 적힌 과거 시각을 현재 운영값으로 확정하지 않는다.

## 5. 데이터 최소 수집

허용된 카카오톡 패키지와 연결 대상 확인 후에만 명령 처리·저장한다. 다른 방의 알림 원문과 개인 대화는 로그·노션·서버로 전달하지 않는다. 대상 방 일반 대화도 기본적으로 보관하지 않고 명령에 필요한 텍스트만 짧게 처리한다.

비밀값은 저장소·빌드 상수·노션에 넣지 않는다. 추후 서버 연동이 필요하면 짧은 범위의 기기별 인증값과 Android Keystore 기반 저장을 검토한다. 앱 백업에 답장 연결 정보·인증값이 자동 포함되지 않게 백업 규칙을 설계한다. 진단 자료는 방 이름·닉네임·메시지·기기 일련번호를 가린 뒤 사용한다.

## 6. API·도메인 계약 제안

| 계약 | 입력 | 결과 |
| --- | --- | --- |
| ScheduleRepository | 검증된 원본 스냅샷 | 적용/오래된 버전/검증 오류 |
| OccurrenceCalculator | 일정, 기준 시각, 시간 범위 | 결정적인 논리 회차 목록 |
| DeliveryPlanner | 회차, 방, 아침 설정 | 생성/변경/취소한 발송 항목 |
| RoomResolver | 패키지, 프로필, 대화 식별 근거 | 연결됨/대기/충돌 |
| Transport.send | 검증된 방 연결 세대, payload | 요청 수락/호출 전 실패/결과 불명 |
| DeliveryCoordinator | 발송 ID, 현재 시각, 실행 세대 | 선점·재검증·호출·상태 저장 |

논리 회차 ID와 실행 세대는 메시지 본문이나 닉네임에서 유추하지 않는다. 실제 외부 회차 ID가 없는 일정은 최초 생성한 회차 ID와 날짜·반복 슬롯 매핑을 저장해 수정 시 유지한다.
