package com.sys.polis.polis_engine.agent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Agent {

    private AgentState currentState;
    // final은 "이 메일박스 참조는 안 바뀐다"는 뜻(메일박스 객체 자체는 한 번 정해지면 교체 안 됨
    private final BlockingQueue<Double> mailbox = new LinkedBlockingQueue<>();

    // 초기 의견값을 가진 AgentState를 생성자에서 받아서 currentState에 할당
    public Agent(AgentState initialState) {
        this.currentState = initialState;
    }

    // 현재 상태를 반환하는 메서드. 다음 상태를 계산할 때 사용.
    public AgentState getCurrentState() {
        return currentState;
    }

    // 계산된 다음 상태를 현재 상태로 교체. updateRule이 새 AgentState를 계산해서 넘겨줄때
    // 해당 메서드에서 교체됨.
    public void applyNextState(AgentState next) {
        this.currentState = next;
    }

    // opinion을 업데이트하는 메서드, 내부 상태를 변경. 현재 의견값을 바로 꺼낼 때 사용.
    public double getOpinion() {
        return currentState.opinion();
    }

    // id를 반환하는 메서드, 행위자 ID를 바로 꺼낼 때 사용
    public int getId() {
        return currentState.id();
    }

    // 받은 메시지 put
    public void receiveMessage(double opinion) throws InterruptedException {
        mailbox.put(opinion);
    }

    // 받은 메시지 take
    public double takeMessages() throws InterruptedException {
        return mailbox.take();
    }

    // 메일박스가 비어 있으면 즉시 null을 반환하는 논블로킹 조회.
    // take()와 달리 상대가 안 보냈을 수도 있는 상황(직접 참조 라우팅에서 이번 틱에 아무도 안 골랐을 때)에 블로킹 없이 확인할 때 쓴다.
    public Double pollMessage() {
        return mailbox.poll();
    }

    // opinion을 업데이트하는 메서드, 내부 상태를 변경
    @Override
    public String toString() {
        return String.format("Agent{id=%d, opinion=%.4f}", getId(), getOpinion());
    }
}