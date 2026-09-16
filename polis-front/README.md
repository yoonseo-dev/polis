# polis-front

M2-5: `polis-server`가 `/topic/snapshots`로 미는 매 틱 스냅샷(히스토그램 20버킷 + 분산 +
극단값 비율)을 구독해 Canvas 히스토그램으로 그리는 화면. 제어 패널에서 REST API
(`/api/simulation/*`, M2-4)로 시작/정지/파라미터 변경을 할 수 있다.

## 실행

```
# 1) polis-server를 8080에서 먼저 띄운다 (repo 루트에서)
./gradlew :polis-server:bootRun

# 2) 이 디렉터리에서 dev 서버 실행
npm install
npm run dev
```

`vite.config.js`의 dev 서버 proxy가 `/api`, `/ws`를 `http://localhost:8080`(polis-server
기본 포트)으로 중계한다 — 백엔드에 CORS 설정을 얹지 않고 프론트만으로 해결한다.

## 확인한 것

threshold를 상전이 지점(plan.md 기준 ~1.2) 아래로 낮추면(예: 0.8) 화면의 히스토그램이
중앙 단봉에서 양 극단 두 봉우리로 갈라지는 것을 관찰함 — 콘솔(M0)에서 봤던 양극화가
같은 모양으로 화면에 재현된다.
