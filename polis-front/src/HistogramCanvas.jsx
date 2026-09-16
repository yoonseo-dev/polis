import { useEffect, useRef } from 'react'

// MetricCollector.BUCKET_COUNT/EXTREME_THRESHOLD과 동일 — 서버 상수와 어긋나면 축 라벨이 틀어진다.
const BUCKET_COUNT = 20
const EXTREME_THRESHOLD = 0.8
const BUCKET_WIDTH = 2.0 / BUCKET_COUNT

function bucketCenter(index) {
  return -1.0 + (index + 0.5) * BUCKET_WIDTH
}

// 일반 DOM이 아니라 Canvas로 그린다(CLAUDE.md 1절 — 행위자 수만 개 시나리오를 겨냥한 선택을
// 히스토그램에도 그대로 적용). 매 틱 스냅샷이 올 때마다 다시 그리는 것만으로 충분히 가볍다.
export function HistogramCanvas({ histogram }) {
  const canvasRef = useRef(null)
  const containerRef = useRef(null)

  useEffect(() => {
    const canvas = canvasRef.current
    const container = containerRef.current
    if (!canvas || !container) return

    const draw = () => {
      const dpr = window.devicePixelRatio || 1
      const width = container.clientWidth
      const height = container.clientHeight
      canvas.width = width * dpr
      canvas.height = height * dpr
      const ctx = canvas.getContext('2d')
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      ctx.clearRect(0, 0, width, height)

      const axisHeight = 24
      const plotHeight = height - axisHeight
      const bars = histogram ?? new Array(BUCKET_COUNT).fill(0)
      const maxCount = Math.max(1, ...bars)
      const barGap = 2
      const barWidth = width / BUCKET_COUNT - barGap

      bars.forEach((count, i) => {
        const barHeight = (count / maxCount) * (plotHeight - 8)
        const x = i * (width / BUCKET_COUNT) + barGap / 2
        const y = plotHeight - barHeight
        const isExtreme = Math.abs(bucketCenter(i)) > EXTREME_THRESHOLD
        ctx.fillStyle = isExtreme ? '#e0575b' : '#5b7fe0'
        ctx.fillRect(x, y, barWidth, barHeight)
      })

      // 중도(0) 기준선
      ctx.strokeStyle = '#c7c7c7'
      ctx.beginPath()
      ctx.moveTo(width / 2, 0)
      ctx.lineTo(width / 2, plotHeight)
      ctx.stroke()

      ctx.fillStyle = '#6b6375'
      ctx.font = '12px system-ui, sans-serif'
      ctx.textAlign = 'left'
      ctx.fillText('-1.0 (극좌)', 2, height - 6)
      ctx.textAlign = 'center'
      ctx.fillText('0 (중도)', width / 2, height - 6)
      ctx.textAlign = 'right'
      ctx.fillText('+1.0 (극우)', width - 2, height - 6)
    }

    draw()
    const resizeObserver = new ResizeObserver(draw)
    resizeObserver.observe(container)
    return () => resizeObserver.disconnect()
  }, [histogram])

  return (
    <div ref={containerRef} style={{ width: '100%', height: '360px' }}>
      <canvas ref={canvasRef} style={{ width: '100%', height: '100%', display: 'block' }} />
    </div>
  )
}
