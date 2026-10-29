검토일: <date start="2026-10-29" time-zone="Asia/Seoul" />
문서 상태: 기술 설계·시험 구현 v0.2. 알림 답장 방식의 한계를 전제로 하며 실제 도착 검증은 별도 기록한다.

## 1. 채택 근거와 변경 대상

MIT 후보의 NotificationListener, SessionManager, SessionReplier는 카카오톡 알림의 답장 Action을 찾아 방별 메모리 캐시에 보관하고 PendingIntent를 호출한다. 원본은 방 이름을 키로 사용하고 실제 도착 확인을 제공하지 않는다.

프로젝트 제안: Kotlin 네이티브 앱에서 알림 파서·방 확인·전송 어댑터를 분리하고, 일정·전송 대기 항목을 영속 DB로 관리한다. 로컬 LLM·대화 자동요약·모든 방 응답은 1차에 가져오지 않는다.

## 2. 대상 방 연결

1. 앱에서 대상 방 이름을 입력하고 유효시간이 있는 1회용 확인 코드를 생성. 새 코드 생성 시 기존 연결·응답·정기 발송을 해제하고 대기 항목을 취소.
2. 봇 폰과 다른 계정의 참여자가 테스트 오픈채팅방에 해당 코드를 입력.
3. 알림을 통해 받은 카카오톡 패키지·안드로이드 사용자 프로필·대화 식별 정보 후보를 추출.
4. 앱에 코드 수신 후보를 표시하고 사용자가 실제로 코드를 보낸 대화인지 로컬 확인.
5. 사용자가 명시적으로 누른 테스트 발송을 다른 참여자의 카카오톡 화면에서 확인한 뒤 운영 방으로 활성화.

코드는 암호학적으로 안전하게 생성하고 앱의 연결 대기 상태에서만 한 번 소비한다. 유효시간 제안은 5분이다. 코드는 사용자 인증이나 관리자 신원 인증 수단이 아니라 이번 연결 후보를 대응시키는 수단이다.

방 이름만으로 운영 발송 대상을 결정하지 않는다. 채널 ID·알림 key·shortcutId는 공급 앱이 정한 값이라 영구적인 카카오 방 ID라고 단정하지 않는다. 파서가 확보한 식별 근거와 범위를 저장하고 버전별로 검증한다. 안전한 구분이 불가능하거나 같은 이름의 후보가 둘 이상이면 연결 대기 상태로 전환한다. 다른 방으로 자동 대체하지 않는다.

### 방 이름이 생략되는 알림 보완

카카오톡 26.9.1 실기기에서 1:1 시험 알림뿐 아니라 isGroupConversation=true인 알림도 방 제목 없이 보낸 사람 이름만 표시했다. 따라서 알림 제목을 항상 방 이름이라고 해석하지 않는다. 실제 구조화된 대화 제목이 설정한 방과 다르면 거부한다. 방 제목이 없으면 5분 연결 대기 중 새 알림의 마지막 메시지가 코드와 정확히 일치하는지 검사한다. 대화 태그 또는 shortcutId가 없는 알림은 후보로 받지 않는다. 일치하지 않은 본문·이전 메시지는 저장하거나 명령으로 처리하지 않는다. 사용자가 실제로 코드를 보낸 대화인지 로컬 확인한 뒤 식별 해시로 묶는다. 수신 서비스 복구 때도 현재 코드가 생성된 뒤 게시된 활성 알림의 마지막 메시지가 미사용·미만료 코드와 일치할 때만 후보를 복구한다. 이전 명령을 재실행하지 않는다.

### 화면의 연결 상태

알림 접근 권한, 알림 수신 서비스 연결, 코드 대기·만료·후보 수신, 방 연결 확정, 답장 준비, 명령 응답 켜짐을 구분한다. 코드·권한·수신·확정 상태 변경은 앱 화면에 즉시 알리고 코드 남은 시간은 화면을 열어둔 동안 갱신한다. 마지막 대상 방 알림 수신 시각을 표시하며 오래된 활성 알림을 재조회한 시각으로 덮어쓰지 않는다. 답장 정보 보관 상태를 카카오 서버의 실시간 접속 확인이나 실제 도착 보장으로 표시하지 않는다.

유효한 코드를 받았으나 사용할 텍스트 답장 Action이 없으면 수신 대기 대신 ‘코드 수신됨 · 답장 기능 확인 필요’로 표시한다. 텍스트 입력은 있으나 소유자 검증에 실패한 경우도 구분한다. 카카오톡 소유자·free-form 텍스트 입력 검증을 유지하고 파일 전용 입력을 거부한다. 진단 기록에는 Action 개수·입력 개수 등만 남기고 대화 본문·참여자 정보는 남기지 않는다.

Android 패키지 조회 제한으로 PendingIntent 소유자 정보가 null·UID -1로 가려지는 경우를 실기기에서 확인했다. Manifest의 queries에 com.kakao.talk 한 패키지만 선언한 뒤 같은 알림의 소유자가 com.kakao.talk과 실제 UID로 반환되는 것을 비교 확인했다. 검증 조건을 완화하는 대신 조회 선언을 보완한다. [Android 패키지 조회 선언](https://developer.android.com/training/package-visibility/declaring)

## 3. 답장 정보와 복구 상태

| 상태 | 의미 | 처리 |
| --- | --- | --- |
| UNBOUND | 연결된 방 없음 | 자동 발송 불가 |
| WAITING_SESSION | 방 등록 정보는 있으나 유효한 답장 Action 없음 | 새 대상 방 알림 대기 |
| READY | 검증된 대상에 사용할 답장 Action 존재 | 전송 시 재검증 |
| AMBIGUOUS | 방 식별이 충돌·불명확 | 자동 발송 중지, 앱에서 재연결 |
| PERMISSION_REQUIRED | 알림 접근 또는 필요한 권한 없음 | 앱에서 권한 안내 |
| PAUSED | 사용자가 일시정지 | 명령 응답·정기 발송 정책에 따라 모두 중지하는 안 제안 |

방 등록·식별 근거는 영속화한다. PendingIntent를 문자열·토큰 파일로 저장하거나 재부팅 후 그대로 복원할 수 있다고 가정하지 않는다.

리스너가 onListenerConnected 된 후에만 현재 활성 알림을 조회해 답장 정보를 재구성한다. 활성 알림이 사라졌다면 새 알림이 올 때까지 WAITING_SESSION을 유지한다. 알림 삭제가 기존 Action을 항상 취소한다는 보장도 없으므로 삭제 시 원본 캐시를 그대로 신뢰하지 말고 상태 재평가·재확보를 수행하는 보수적 설계안으로 둔다.

앱 프로세스 종료·폰 재부팅·카카오톡 재시작·권한 해제·앱 업데이트를 각각 시험한다. 앱 강제 중지는 자동 복구를 보장하지 않으며 사용자가 다시 앱을 열어 상태를 확인해야 한다.

## 4. 발송과 중복 방지

발송 항목 상태: PENDING → CLAIMED → DISPATCHING → REQUEST_ACCEPTED 또는 DELIVERY_UNKNOWN. 전송 요청 전 문제가 확정되면 WAITING_SESSION/FAILED_PRE_SEND, 유효시각을 넘으면 EXPIRED, 설정 변경이면 CANCELLED.

- DB에 UNIQUE(room_id, occurrence_id, notice_kind, part_id)를 둔다. schedule_revision을 고유 키에 넣어 일정 수정만으로 같은 안내가 중복 생성되지 않게 한다.
- 발송 작업은 DB 트랜잭션으로 한 작업자만 선점하고, 실제 전송 직전 최신 run_generation·schedule_revision·활성 여부·기한·방 식별을 다시 확인한다.
- CLAIMED까지는 아직 전송 API를 호출하지 않은 상태다. 선점 lease가 만료되고 미호출이 확실할 때만 안전하게 회수.
- 호출 직전에 DISPATCHING을 영속화한다. 이 상태에서 앱이 죽으면 실제 호출 여부가 불명확하므로 DELIVERY_UNKNOWN으로 복구하고 자동 재전송하지 않는다.
- PendingIntent.send가 정상 반환해도 결과는 REQUEST_ACCEPTED다. 카카오톡 서버 수신·방 표시·사람의 읽음은 확인되지 않는다.
- 호출 뒤 오류가 나면 실패가 확실하다고 단정하지 않는다. 입증 가능한 호출 전 실패 외에는 DELIVERY_UNKNOWN으로 남기고 자동 재시도를 막는다.
- 정확한 end-to-end 1회 도착을 보장한다고 표현하지 않는다. 중복 요청을 최대한 막는 대신 미확인 누락 가능성을 기록한다.

방별 전송은 직렬화한다. 이름·확인 코드·메시지 본문을 전송 목적지로 해석하지 않는다. 카카오톡 패키지와 PendingIntent creatorPackage, 허용된 입력 Action을 재검증하고 다른 패키지의 Action은 거부한다.

## 5. 정확한 시각과 절전

제안: 다음 알림 시각의 one-shot AlarmManager 알람을 등록하고 발동 시 같은 시각의 모든 적격 항목을 처리한다. 반복 WorkManager로 5분 전 알림을 구현하지 않는다. WorkManager는 유예 가능한 데이터 동기화·정리에만 사용한다.

Android 공식 문서는 WorkManager 주기 작업 최소 간격이 15분이고 실행 시점은 시스템 제약에 영향을 받는다고 설명한다. 정확한 알람 권한이 필요한 버전에서는 권한이 없으면 5분 전 기능을 '권한 필요'로 표시하며 조용히 부정확한 방식으로 대체하지 않는다.

Doze에서는 allow-while-idle 계열 알람도 앱당 9분 제한을 받는다고 안내한다. 가까운 서로 다른 알림 시각·수신 명령 즉시응답·카카오톡 자체 네트워크 상태를 함께 시험해야 한다. 권한이나 상시 알림 하나로 정시 발송을 보장한다고 가정하지 않는다. 형식적인 알람시계 권한·상시 서비스로 제한을 우회하는 설계를 기본값으로 두지 않는다.

폰 재부팅·시간 변경·시간대 변경·앱 업데이트·권한 재허용 때 미래 항목만 재계산한다. 알람이 울려도 답장 세션이 없으면 재연결을 요구하며, 사라진 세션을 새로 만들어낼 수 있다고 설명하지 않는다.

## 6. 출처

- [검토한 SessionManager](https://github.com/bssm-oss/kakao-talk-auto-bot/blob/a10b27b46984b8b85712bf84bf452596f607f5c7/app/src/main/java/com/example/chatbotchichi/SessionManager.kt)
- [검토한 SessionReplier](https://github.com/bssm-oss/kakao-talk-auto-bot/blob/a10b27b46984b8b85712bf84bf452596f607f5c7/app/src/main/java/com/example/chatbotchichi/SessionReplier.kt)
- [Android 활성 알림 조회](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [Android 알람](https://developer.android.com/develop/background-work/services/alarms)
- [Doze 제한](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [WorkManager 주기 작업](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)

## 관련 상세 설계

<mention url="https://app.notion.com/p/408e60ccff0c81278567c882188341da?pvs=204">02 일정 알림 상세 기획</mention>

<mention url="https://app.notion.com/p/408e60ccff0c81c9bfebe9bb15fc5db9?pvs=204">04 아키텍처·데이터·기존 봇 연동</mention>

<mention url="https://app.notion.com/p/408e60ccff0c81ce9e49f2cf4c5a7018?pvs=204">07 개발 계획·실기기 검증</mention>
