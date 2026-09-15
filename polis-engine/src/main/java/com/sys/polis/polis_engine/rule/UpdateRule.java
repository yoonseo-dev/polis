package com.sys.polis.polis_engine.rule;

import com.sys.polis.polis_engine.agent.AgentState;

import java.util.List;

public interface UpdateRule {
    AgentState update(AgentState self, List<AgentState> neighbors);
}
