import { useEffect, useState } from 'react'
import { ControlPanel } from './ControlPanel.jsx'
import { HistogramCanvas } from './HistogramCanvas.jsx'
import { useSnapshotStream } from './useSnapshotStream.js'
import { startSimulation, stopSimulation, updateParams, fetchStatus } from './api.js'
import './index.css'

// M2-5: React + Canvas 히스토그램. /topic/snapshots를 그려서 "콘솔에서 봤던 양극화가
// 화면에서 같은 모양으로 전개되는가"(plan.md M2-5 검증 문구)를 눈으로 확인하는 화면.
function App() {
  const { connected, snapshot } = useSnapshotStream()
  const [status, setStatus] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    fetchStatus().then(setStatus).catch((e) => setError(e.message))
  }, [])

  const runAction = (action) => async (params) => {
    try {
      setStatus(await action(params))
      setError(null)
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <main style={{ maxWidth: '720px', margin: '0 auto', padding: '24px' }}>
      <h1>Polis — 의견 분포</h1>
      <p>
        WebSocket: {connected ? '연결됨' : '연결 안 됨'} · 시뮬레이션:{' '}
        {status?.running ? '실행 중' : '정지'}
        {status ? ` · N=${status.agentCount}` : ''}
      </p>
      {error && <p style={{ color: '#e0575b' }}>{error}</p>}

      <ControlPanel
        status={status}
        onStart={runAction(startSimulation)}
        onStop={runAction(stopSimulation)}
        onApplyParams={runAction(updateParams)}
      />

      <HistogramCanvas histogram={snapshot?.histogram} />

      <dl style={{ display: 'flex', gap: '24px' }}>
        <div>
          <dt>tick</dt>
          <dd>{snapshot?.tick ?? '-'}</dd>
        </div>
        <div>
          <dt>분산</dt>
          <dd>{snapshot ? snapshot.variance.toFixed(4) : '-'}</dd>
        </div>
        <div>
          <dt>극단값 비율(|opinion|&gt;0.8)</dt>
          <dd>{snapshot ? `${(snapshot.extremeRatio * 100).toFixed(1)}%` : '-'}</dd>
        </div>
      </dl>
    </main>
  )
}

export default App
