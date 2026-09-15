package com.sys.polis.polis_engine.rule;

import com.sys.polis.polis_engine.agent.AgentState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// 끌림/밀어냄 테스트
class AttractionRepulsionRuleTest {

    @Test
    void 끌림_임계값_이하면_가까워짐() {
        UpdateRule rule = new AttractionRepulsionRule(0.5, 0.4);
        //학습률이 0.5, 끌림/밀어냄 임계값이 0.4
        AgentState self = new AgentState(1, 0.3);
        AgentState neighbor = new AgentState(2, 0.5);

        //임계값이 neighbor의 의견값보다 크고 self의 의견값보다 작음
        //-> self가 neighbor과 가까워진다!

        AgentState result = rule.update(self, List.of(neighbor));

        //기대값, 실제값, 오차 허용치
        //허용치 : "정확히 같은가"가 아니라 "충분히 가까운가(오차 1e-9 이내)"로 검사
        assertEquals(0.4, result.opinion(), 1e-9);
    }

    @Test
    void 밀어냄_임계값_초과면_멀어짐(){
        UpdateRule rule = new AttractionRepulsionRule(0.5, 0.3);
        //임계값: 0.3, 학습률: 0.5
        AgentState self = new AgentState(1, 0.1);
        //내 의견
        AgentState neighbor = new AgentState(2, 0.5);
        //상대 의견

        AgentState result = rule.update(self, List.of(neighbor));

        assertEquals(-0.1, result.opinion(), 1e-9);
    }

    //클램프 케이스 — 계산 결과가 [-1, +1]을 넘으려 할 때 잘려서 정확히 1.0이나 -1.0이 되는지.

    //경계값 케이스 — 차이가 임계값과 정확히 같을 때
}