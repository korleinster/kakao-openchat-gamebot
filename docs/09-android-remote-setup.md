확인일: <date start="2026-10-29" time-zone="Asia/Seoul" />
상태: Mac 도구·폰 USB 디버깅 연결·앱 정보 화면 표시·ADB 스크롤 조작 확인 완료.

## 1. 준비 결과

| 항목 | 확인 결과 |
| --- | --- |
| Google Platform-Tools | 37.0.1, ADB 1.0.41 / build 15733141 실행 확인 |
| scrcpy | 공식 macOS ARM64 5.0.1 실행 확인 |
| 도구 위치 | 프로젝트 work/android-remote/tools. 시스템 전역 PATH나 Homebrew 설치를 변경하지 않음 |
| 다운로드 무결성 | ADB는 Google 공식 SDK 메타데이터 체크섬 일치. scrcpy는 공식 SHA-256 일치 |
| 폰 승인 | 사용자가 USB 디버깅 활성화·연결 허용. ADB 상태 device 확인 |
| 실기기 | SM-G986N (Galaxy S20+), Android 13 / API 33, One UI 50100 (5.1) |
| 카카오톡 | 폰 패키지에서 26.9.1 / versionCode 29260910 직접 확인 |
| USB 구성 | rndis,adb. USB 테더링과 ADB 동시 활성 확인 |
| 네트워크 | 연결 전후 기본 경로·DNS 동일. feth0 active, TetherKit running, HTTPS 200 |

## 2. 재사용 명령

아래는 프로젝트 루트에서 실행한다. 도구 바이너리와 원시 로그·스크린샷·기기 일련번호는 Git에 포함하지 않는다.

```sh
scripts/adb.sh devices -l
scripts/adb.sh shell getprop ro.product.model
scripts/adb.sh shell dumpsys package com.kakao.talk
scripts/scrcpy.sh --no-control
scripts/scrcpy.sh
```

adb.sh는 USB 장치만 선택한다. scrcpy.sh는 이번 기기에서 영상 시작을 확인한 --force-adb-forward와 최대 크기 1280을 기본으로 두고 오디오·클립보드 자동 동기화·시작 시 화면 자동 깨우기를 끈다. --no-control은 화면 보기 전용이며 생략하면 마우스·키보드 조작을 허용하는 모드다. 잠긴 폰의 암호·생체인증을 우회하지 않는다. 잠금 해제가 필요한 경우 사용자가 폰에서 직접 해제한다.

ADB 인증키는 ADB가 Mac 표준 .android 설정 폴더에서 관리한다. 키 본문·폰 일련번호는 문서·채팅·저장소에 기록하지 않는다. 공개 네트워크 ADB 포트나 무선 디버깅은 활성화하지 않았다.

## 3. 실제 수행한 확인

1. 승인된 USB 장치가 한 대이고 상태가 device인지 확인.
2. 폰 OS·카카오톡 버전과 USB 구성 읽기 조회.
3. 카카오톡 앱 정보 화면 열기 명령 실행.
4. scrcpy USB forward 연결에서 Metal 렌더러·VideoToolbox 디코딩·576×1280 영상 텍스처 확인.
5. 최초 잠금 상태는 사용자가 직접 해제. 이후 카카오톡 앱 정보 화면을 열고 ADB 스크롤 후 화면 하단의 버전 26.9.1을 실제 캡처로 확인.
6. 연결 전후 Mac 경로·DNS·테더링 서비스 및 HTTPS 응답 비교.

화면 캡처 파일이 생성되거나 화면 열기 명령이 성공했다고 실제 앱 정보 화면을 눈으로 확인한 것으로 표시하지 않는다. 앱 설치, 봇 실행, 카카오톡 메시지 발송 시험은 아직 수행하지 않았다.

이번 시험의 기본 reverse 연결에서는 영상 시작이 지연됐고 forward 연결에서는 디코딩·렌더러 초기화를 확인했다. 원인 전체를 규명한 것은 아니며 재현된 연결 방식을 실행 도우미에 기록했다. forward는 USB를 통한 로컬 터널이며 폰의 공개 네트워크 ADB 포트를 여는 설정이 아니다. 최초 확인용 미러링은 --no-control로 실행했고 화면 조작은 ADB 명령으로 검증했다.

## 4. 테더링 보존과 운영 범위

사용자가 직접 USB 디버깅을 켜는 동안 구성 재연결이 있었을 수 있으며 순간 단절 여부를 연속 측정한 것은 아니다. 확인 시점에는 rndis,adb와 네트워크가 정상이다. 에이전트는 테더링 OFF, 기본 USB 구성 변경, USB 장치 리셋, 폰 재부팅, TetherKit 재시작, 경로·DNS·Tailscale 설정 변경을 수행하지 않았다.

이후 원격 조작은 개발 앱 설치·기기 상태·테스트 방 시험에 필요한 범위에서 수행한다. 카카오톡 계정 설정 변경이나 실제 운영방 발송은 기능별 승인 범위에서 구분한다.

## 5. 출처

- [Google Platform-Tools](https://developer.android.com/tools/releases/platform-tools)
- [ADB 연결·최초 승인](https://developer.android.com/tools/adb)
- [scrcpy 공식 macOS 안내](https://github.com/Genymobile/scrcpy/blob/master/doc/macos.md)
