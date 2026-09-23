package com.sys.polis.polis_engine.rule;

import com.sys.polis.polis_engine.agent.AgentState;

import java.util.List;

/**
 * 갱신 규칙 전략 인터페이스(M3-1에서 확정). 엔진(Simulation 등)이 아는 규칙은 이 인터페이스 하나뿐이고,
 * 모델을 바꾸려면 구현체만 교체한다.
 *
 * <p>구현체가 지켜야 할 계약:
 * <ul>
 *   <li><b>입력:</b> {@code self}는 이번 틱 시작 시점의 자기 상태, {@code neighbors}는 이번 틱에 만난
 *       이웃들의 상태(발신자 id 오름차순 — 반영 순서까지 시드만으로 재현되도록 엔진이 정렬해서 넘긴다).
 *       {@code id}는 발신자 id이고 {@code opinion}은 보낸 시점의 값이다.</li>
 *   <li><b>호출 조건:</b> 엔진은 이웃을 한 명도 못 만난 행위자에 대해서는 호출하지 않는다(그 행위자의 상태는
 *       그대로 유지). 그래도 구현체는 빈 리스트가 오면 {@code self}를 그대로 반환하는 것이 안전하다.</li>
 *   <li><b>출력:</b> 다음 상태를 새 객체로 반환한다. 입력 객체는 수정하지 않는다({@code AgentState}는 불변).
 *       [-1, +1] 범위 보장은 {@code AgentState.withOpinion()}이 clamp로 처리한다.</li>
 *   <li><b>스레드 안전성:</b> 규칙 인스턴스 하나를 전체 행위자가 공유하고, 한 틱 안에서 여러 가상 스레드가
 *       동시에 {@code update()}를 호출한다. 따라서 구현체는 (a) 호출 간 가변 상태를 갖지 않거나,
 *       (b) 가진다면 스레드 안전해야 한다(예: 카운터는 {@code LongAdder}, 런타임 변경 파라미터는
 *       {@code volatile}).</li>
 *   <li><b>결정성:</b> 같은 입력이면 같은 출력을 내야 한다. 무작위성이 필요하면 호출 순서에 좌우되는 공유
 *       {@code Random}을 쓰지 말고, 재현성을 해치지 않는 방식을 택한다(M0 앵커 대조가 깨진다).</li>
 * </ul>
 */
@FunctionalInterface
public interface UpdateRule {
    AgentState update(AgentState self, List<AgentState> neighbors);
}
