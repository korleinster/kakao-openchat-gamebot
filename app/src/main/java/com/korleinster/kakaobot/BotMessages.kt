package com.korleinster.kakaobot

enum class BotTone(val label: String, val description: String) {
    BOYFRIEND("남친", "다정하고 편안하게"), GIRLFRIEND("여친", "발랄하고 다정하게"),
    KID("잼민이", "장난기 있게 가볍게"), ELDER("어르신", "느긋한 옛말투로"),
    ASSISTANT("담백한 비서", "간결하고 차분하게")
}
enum class MessageKind { PING, HELP, GAME, ACK, REMINDER, BRIEFING, INFO }
object BotMessages {
    fun render(tone: BotTone, kind: MessageKind, content: String): String {
        if(kind == MessageKind.PING) return when(tone) {
            BotTone.BOYFRIEND -> "퐁! 여기 있어. 연결 잘 되어 있어."
            BotTone.GIRLFRIEND -> "퐁! 잘 들려, 연결 정상이야 💕"
            BotTone.KID -> "퐁ㅋㅋ 연결 이상 없음!"
            BotTone.ELDER -> "퐁, 잘 들리는구먼. 연결은 정상일세."
            BotTone.ASSISTANT -> "퐁! 연결이 정상이에요."
        }
        val prefix = when(tone) {
            BotTone.BOYFRIEND -> when(kind) {
                MessageKind.GAME -> "내가 골라봤어."
                MessageKind.ACK -> "알겠어, 내가 챙겨둘게."
                MessageKind.REMINDER, MessageKind.BRIEFING -> "잊지 않게 챙겨줄게."
                MessageKind.HELP -> "여기서 이렇게 불러주면 돼."
                else -> "확인해 봤어."
            }
            BotTone.GIRLFRIEND -> when(kind) {
                MessageKind.GAME -> "두근두근, 결과 나왔어 💕"
                MessageKind.ACK -> "좋아, 내가 챙겨줄게 💕"
                MessageKind.REMINDER, MessageKind.BRIEFING -> "놓치지 말라고 알려줄게 💕"
                MessageKind.HELP -> "이렇게 불러줘 💕"
                else -> "확인했어 💕"
            }
            BotTone.KID -> when(kind) {
                MessageKind.GAME -> "결과 나왔다ㅋㅋ"
                MessageKind.ACK -> "접수 완료! 까먹기 없기ㅋㅋ"
                MessageKind.REMINDER, MessageKind.BRIEFING -> "일정 알림 출동!"
                MessageKind.HELP -> "쓸 수 있는 명령 모음!"
                else -> "확인 완료!"
            }
            BotTone.ELDER -> when(kind) {
                MessageKind.GAME -> "어디, 결과를 보세."
                MessageKind.ACK -> "알겠네. 때가 되면 알려주겠네."
                MessageKind.REMINDER, MessageKind.BRIEFING -> "잊기 전에 살펴보게."
                MessageKind.HELP -> "이렇게 부르면 된다네."
                else -> "확인한 내용을 전하겠네."
            }
            BotTone.ASSISTANT -> when(kind) {
                MessageKind.GAME -> "선택 결과를 안내해요."
                MessageKind.ACK -> "알림을 예약했어요."
                MessageKind.HELP -> "사용 가능한 명령이에요."
                else -> "알려드려요."
            }
        }
        return "$prefix\n$content"
    }
    fun preview(tone: BotTone): String = render(tone, MessageKind.REMINDER, "[반복 알림] 산책\n시작: 18:00 (한국 시간)")
}
