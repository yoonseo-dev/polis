// 행위자 간에 mailbox로 주고받는 메시지. CLAUDE.md 패키지 구조에 명세돼 있었으나 누락돼 있던 클래스.
// opinion만 담아 보내면 받는 쪽이 "누가 보냈는지" 알 수 없어(이전 구현의 실제 버그),
// 여러 발신자의 메시지를 발신자 id로 정렬하는 등의 처리가 불가능하다 — senderId를 함께 담아 해결한다.
package com.sys.polis.polis_engine.agent;

public record Message(int senderId, double opinion) {
}
