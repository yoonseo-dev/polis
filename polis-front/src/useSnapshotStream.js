import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'

const SNAPSHOT_TOPIC = '/topic/snapshots'

// M2-3/M2-4가 /topic/snapshots로 미는 MetricCollector.Snapshot(tick, histogram, variance,
// extremeRatio)을 그대로 구독한다. 서버(WebSocketConfig)가 SockJS 없이 순수 WebSocket만
// 열어두므로 여기서도 SockJS를 얹지 않는다 — brokerURL 하나면 충분하다(KISS).
export function useSnapshotStream() {
  const [connected, setConnected] = useState(false)
  const [snapshot, setSnapshot] = useState(null)
  const clientRef = useRef(null)

  useEffect(() => {
    const client = new Client({
      brokerURL: `ws://${window.location.host}/ws`,
      reconnectDelay: 2000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(SNAPSHOT_TOPIC, (message) => {
          setSnapshot(JSON.parse(message.body))
        })
      },
      onWebSocketClose: () => setConnected(false),
    })
    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
    }
  }, [])

  return { connected, snapshot }
}
