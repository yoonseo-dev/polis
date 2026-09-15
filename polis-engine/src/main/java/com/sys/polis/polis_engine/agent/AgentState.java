package com.sys.polis.polis_engine.agent;

import static java.lang.Math.clamp;

public record AgentState(int id, double opinion) {

    // -1에서 1까지의 범위를 초과할 때의 예외처리
    public AgentState {
        if (opinion < -1.0 || opinion > 1.0) {
            throw new IllegalArgumentException(
                    "opinion 범위 초과: " + opinion + " (허용 범위: -1.0 ~ +1.0)");
        }
    }

    // opinion을 업데이트하는 메서드, 수정 대신 새 객체를 생성하고 반환.
    public AgentState withOpinion(double newOpinion) {
        double clamped = Math.clamp(newOpinion, -1.0, 1.0);
        return new AgentState(this.id(), clamped);// clamped -> opinion
    }
}