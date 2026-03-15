package com.jongmin.ai.role_playing.platform.entity

/**
 * @author Jongmin
 * @since  2025. 2. 26
 */
//@Entity
//@Table(
//  indexes = [
//    Index(name = "idx_actor_createdAt", columnList = "createdAt"),
//    Index(name = "idx_actor_status", columnList = "status"),
//    Index(name = "idx_actor_accountId", columnList = "accountId"),
//    Index(name = "idx_actor_ownerId", columnList = "ownerId"),
//    Index(name = "idx_actor_type", columnList = "type"),
//  ]
//)
data class Actor(
  val id: String
) {
  fun action(): String {
    /**
     * OOC 를 잘 적용해야한다.
     * OOC는 **"Out Of Character"**의 약자로, 역할극(RP) 중 캐릭터의 입장을 벗어나 사용자 본인이 직접 의사소통할 때 사용됩니다. 주로 다음과 같은 목적으로 활용됩니다:
     *
     * 메타적 지시:
     * 예) "[OOC: 다음 장면에서 좀비가 나타나게 해주세요.]"
     * → LLM(인공지능)에게 스토리 방향, 설정 변경 등을 요청할 때 사용합니다.
     *
     * 설명 또는 수정:
     * 예) "[OOC: 제 캐릭터는 아직 그 비밀을 모릅니다.]"
     * → 캐릭터의 지식/행동에 대한 보충 설명을 추가합니다.
     *
     * 분리된 대화:
     * 예) "[OOC: 이 부분 실제 역사와 다르네요. 수정할까요?]"
     * → 역할극 흐름을 방해하지 않으면서 현실적인 논의를 진행합니다
     */
    return "inference" // 추론이 식작됨.
  }
}
