import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { requestMIDIAccess } from 'react-native-midi-api';
import type { MIDIAccess, MIDIOutput } from 'react-native-midi-api';

// ─── Types ───────────────────────────────────────────────────────────────────

export interface MidiSettings {
  ccNumber: number;
  midiChannel: number;
  inverted: boolean;
  smoothingAlpha: number;    // 0.05 = very smooth/slow, 1.0 = raw/instant
  deadzone: number;          // min CC change before output fires (0–20)
  wifiHost: string;
  wifiPort: number;
  calibrationClosed: number;
  calibrationOpen: number;
  facingFront: boolean;
}

export interface UsbMidiState {
  scanning: boolean;
  available: boolean;       // MIDIAccess successfully obtained from OS
  connected: boolean;       // ≥1 output port enumerated
  transmitting: boolean;    // sent within last 400 ms
  portName: string;
  portCount: number;
  error: string | null;
}

export type WifiStatus =
  | 'idle'
  | 'connecting'
  | 'connected'
  | 'error'
  | 'disconnected';

export type ActiveOutput = 'usb' | 'wifi' | 'none';

interface MidiContextValue {
  settings: MidiSettings;
  updateSettings: (partial: Partial<MidiSettings>) => void;
  // USB MIDI — primary output
  usbStatus: UsbMidiState;
  refreshUsb: () => void;
  // Wi-Fi MIDI — secondary/optional output
  wifiStatus: WifiStatus;
  connectWifi: () => void;
  disconnectWifi: () => void;
  // Common
  sendCC: (value: number) => void;
  lastCCValue: number;
  activeOutput: ActiveOutput;
}

// ─── Defaults ────────────────────────────────────────────────────────────────

const STORAGE_KEY = '@mouthmidi_v2';

const DEFAULT_SETTINGS: MidiSettings = {
  ccNumber: 74,
  midiChannel: 1,
  inverted: false,
  smoothingAlpha: 0.15,
  deadzone: 2,
  wifiHost: '',
  wifiPort: 3000,
  calibrationClosed: 0.15,
  calibrationOpen: 0.38,
  facingFront: true,
};

const USB_INITIAL: UsbMidiState = {
  scanning: false,
  available: false,
  connected: false,
  transmitting: false,
  portName: '',
  portCount: 0,
  error: null,
};

// ─── Context ─────────────────────────────────────────────────────────────────

const MidiContext = createContext<MidiContextValue | null>(null);

export function MidiProvider({ children }: { children: React.ReactNode }) {

  // ── Settings ───────────────────────────────────────────────────────────────
  const [settings, setSettings] = useState<MidiSettings>(DEFAULT_SETTINGS);
  const settingsRef = useRef(settings);
  useEffect(() => { settingsRef.current = settings; }, [settings]);

  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const latestSettingsRef = useRef<MidiSettings>(DEFAULT_SETTINGS);

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY).then((raw) => {
      if (raw) {
        try { setSettings((prev) => ({ ...prev, ...JSON.parse(raw) })); }
        catch { /* ignore corrupted storage */ }
      }
    });
  }, []);

  const updateSettings = useCallback((partial: Partial<MidiSettings>) => {
    setSettings((prev) => {
      const next = { ...prev, ...partial };
      latestSettingsRef.current = next;
      if (persistTimerRef.current) clearTimeout(persistTimerRef.current);
      persistTimerRef.current = setTimeout(() => {
        AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(latestSettingsRef.current));
      }, 300);
      return next;
    });
  }, []);

  // ── USB MIDI ───────────────────────────────────────────────────────────────
  const [usbStatus, setUsbStatus] = useState<UsbMidiState>(USB_INITIAL);
  const midiAccessRef = useRef<MIDIAccess | null>(null);
  const midiOutputRef = useRef<MIDIOutput | null>(null);
  const isUsbTransmittingRef = useRef(false);
  const usbTransmitTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const refreshOutputs = useCallback((access: MIDIAccess) => {
    const outputs = Array.from(access.outputs.values());
    if (outputs.length > 0) {
      midiOutputRef.current = outputs[0];
      setUsbStatus((prev) => ({
        ...prev,
        scanning: false,
        available: true,
        connected: true,
        portName: outputs[0].name ?? 'Android MIDI',
        portCount: outputs.length,
        error: null,
      }));
    } else {
      midiOutputRef.current = null;
      setUsbStatus((prev) => ({
        ...prev,
        scanning: false,
        available: true,
        connected: false,
        portName: '',
        portCount: 0,
        error: 'No MIDI outputs found.\nGo to Settings → Developer Options → USB → MIDI, then tap Refresh.',
      }));
    }
  }, []);

  const initUsb = useCallback(async () => {
    setUsbStatus((prev) => ({ ...prev, scanning: true, error: null }));
    try {
      const access = await requestMIDIAccess({ sysex: false });
      midiAccessRef.current = access;
      refreshOutputs(access);
      // Listen for USB plug/unplug and mode changes
      access.onstatechange = () => {
        if (midiAccessRef.current) refreshOutputs(midiAccessRef.current);
      };
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setUsbStatus({
        scanning: false,
        available: false,
        connected: false,
        transmitting: false,
        portName: '',
        portCount: 0,
        error: `MIDI access failed: ${msg}`,
      });
    }
  }, [refreshOutputs]);

  useEffect(() => { initUsb(); }, [initUsb]);

  // ── Wi-Fi MIDI ─────────────────────────────────────────────────────────────
  const [wifiStatus, setWifiStatus] = useState<WifiStatus>('idle');
  const wsRef = useRef<WebSocket | null>(null);

  const connectWifi = useCallback(() => {
    // Use latestSettingsRef (synchronously updated) rather than settingsRef
    // (updated via useEffect) to avoid a stale-host race when the user edits
    // the host field and immediately presses Connect.
    const s = latestSettingsRef.current;
    const host = s.wifiHost.trim();
    if (!host) return;

    if (wsRef.current) {
      wsRef.current.onopen = null;
      wsRef.current.onerror = null;
      wsRef.current.onclose = null;
      wsRef.current.close();
      wsRef.current = null;
    }
    setWifiStatus('connecting');

    try {
      const ws = new WebSocket(`ws://${host}:${s.wifiPort}`);
      ws.onopen = () => setWifiStatus('connected');
      ws.onerror = () => setWifiStatus('error');
      ws.onclose = () => {
        if (wsRef.current === ws) setWifiStatus('disconnected');
      };
      wsRef.current = ws;
    } catch {
      setWifiStatus('error');
    }
  }, []);

  const disconnectWifi = useCallback(() => {
    if (wsRef.current) {
      wsRef.current.onopen = null;
      wsRef.current.onerror = null;
      wsRef.current.onclose = null;
      wsRef.current.close();
      wsRef.current = null;
    }
    setWifiStatus('idle');
  }, []);

  // ── sendCC ─────────────────────────────────────────────────────────────────
  const lastSendTimeRef = useRef(0);
  const lastCCSentRef = useRef(-99);
  const [lastCCValue, setLastCCValue] = useState(0);

  const sendCC = useCallback((value: number) => {
    const clamped = Math.max(0, Math.min(127, Math.round(value)));
    const now = Date.now();
    const s = settingsRef.current;

    // Throttle to ~60 fps
    if (now - lastSendTimeRef.current < 16) return;
    // Dead zone: skip if change is below threshold
    if (
      Math.abs(clamped - lastCCSentRef.current) < s.deadzone &&
      lastCCSentRef.current !== -99
    ) return;

    lastSendTimeRef.current = now;
    lastCCSentRef.current = clamped;
    setLastCCValue(clamped);

    // Standard MIDI CC message bytes
    const statusByte = 0xB0 | ((s.midiChannel - 1) & 0x0F);
    const msg: number[] = [statusByte, s.ccNumber & 0x7F, clamped];

    // ── USB MIDI (primary) ──
    if (midiOutputRef.current) {
      try {
        midiOutputRef.current.send(msg);
        // Throttled transmitting indicator — only setState on transitions
        if (!isUsbTransmittingRef.current) {
          isUsbTransmittingRef.current = true;
          setUsbStatus((prev) => ({ ...prev, transmitting: true }));
        }
        if (usbTransmitTimerRef.current) clearTimeout(usbTransmitTimerRef.current);
        usbTransmitTimerRef.current = setTimeout(() => {
          isUsbTransmittingRef.current = false;
          setUsbStatus((prev) => ({ ...prev, transmitting: false }));
        }, 400);
      } catch {
        // Port disappeared — onstatechange will update status
      }
    }

    // ── Wi-Fi WebSocket (secondary) ──
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(
        JSON.stringify({
          type: 'cc',
          channel: s.midiChannel,
          cc: s.ccNumber,
          value: clamped,
        }),
      );
    }
  }, []);

  // ── Cleanup on unmount ─────────────────────────────────────────────────────
  useEffect(() => {
    return () => {
      // Remove MIDI state-change listener
      if (midiAccessRef.current) {
        midiAccessRef.current.onstatechange = null;
      }
      // Cancel pending transmit-indicator timer
      if (usbTransmitTimerRef.current) clearTimeout(usbTransmitTimerRef.current);
      // Close WebSocket without triggering status updates
      if (wsRef.current) {
        wsRef.current.onopen = null;
        wsRef.current.onerror = null;
        wsRef.current.onclose = null;
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, []);

  // ── activeOutput ────────────────────────────────────────────────────────────
  const activeOutput: ActiveOutput =
    usbStatus.connected ? 'usb' :
    wifiStatus === 'connected' ? 'wifi' : 'none';

  return (
    <MidiContext.Provider
      value={{
        settings,
        updateSettings,
        usbStatus,
        refreshUsb: initUsb,
        wifiStatus,
        connectWifi,
        disconnectWifi,
        sendCC,
        lastCCValue,
        activeOutput,
      }}
    >
      {children}
    </MidiContext.Provider>
  );
}

export function useMidi() {
  const ctx = useContext(MidiContext);
  if (!ctx) throw new Error('useMidi must be used within MidiProvider');
  return ctx;
}
