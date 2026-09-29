## BLE-Verbindungsaufbau mit eigner Event-Schleife, keine Race-Condition

**Problem:**
Race-Condition: Nach `Ready` konnte DISCONNECTED-Callback während Job-Ende eintreffen und die Reconnect-Initialisierung reauslösen.

**Lösung:**
- `startConnectionLoop()` eignet die ganze GATT-Lebensdauer; `gattLock` synchronized connect()/disconnect()/closeGatt().
- Timeout/Fehler → Retry nach Backoff (3/5/10/30 s-Obergrenze, endlos ohne Giveup).
- Identity-Filter für verwaiste Callbacks.
- `SecurityException` bei connectGatt → permanent Error.
- `onServicesDiscovered` / `onDescriptorWrite` Fehler: transient (Status != SUCCESS) → Error + disconnect + Retry; permanent (HR-Merkmal/CCCD fehlt) → Error + reconnectEnabled=false + disconnect (kein Retry).
- Connecting-Status triggert Auto-Pause (selbstheilend bei stale Ready).
- HR-Sample-Buffer 64 → 256 (≈4 Min. Puffern); tryEmit mit Ordering wie empfangen.

**Dateien:**
- `code/app/src/main/kotlin/com/kevin/hrtracker/ble/HrBleManager.kt`
- `code/app/src/main/kotlin/com/kevin/hrtracker/service/HrRecordingService.kt`
- `code/app/src/test/kotlin/com/kevin/hrtracker/ble/ReconnectDelayTest.kt` (new)
