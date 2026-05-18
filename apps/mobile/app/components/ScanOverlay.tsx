import React from 'react';
import { View, StyleSheet, Text, Dimensions } from 'react-native';
import Svg, { Rect, Circle, Defs, LinearGradient, Stop } from 'react-native-svg';
import { CoverageGrid, MissingRegion } from '../hooks/useScanCoverage';

const { width: SCREEN_W } = Dimensions.get('window');
const OVERLAY_W = SCREEN_W - 32;
const OVERLAY_H = OVERLAY_W * 0.75; // 4:3 aspect ratio for camera preview
const PADDING = 4;

interface ScanOverlayProps {
  coverageGrid: CoverageGrid;
  coveragePercent: number;
  missingRegions: MissingRegion[];
  isScanning: boolean;
  elapsedMs: number;
  autoStopDelayMs: number;
  showGuidance?: boolean;
}

export default function ScanOverlay({
  coverageGrid,
  coveragePercent,
  missingRegions,
  isScanning,
  elapsedMs,
  autoStopDelayMs,
  showGuidance = true,
}: ScanOverlayProps) {
  const { rows, cols, cells } = coverageGrid;
  const cellW = (OVERLAY_W - PADDING * 2) / cols;
  const cellH = (OVERLAY_H - PADDING * 2) / rows;

  const formatTime = (ms: number) => {
    const s = Math.floor(ms / 1000);
    const m = Math.floor(s / 60);
    const rem = s % 60;
    return `${m}:${rem.toString().padStart(2, '0')}`;
  };

  // Guidance: pick first missing region
  const guidance = missingRegions.length > 0 && showGuidance
    ? `Scan ${missingRegions[0].label}`
    : coveragePercent >= 95
    ? 'Almost done — hold steady'
    : 'Move slowly around the product';

  return (
    <View style={styles.container}>
      {/* Mesh grid */}
      <Svg width={OVERLAY_W} height={OVERLAY_H} viewBox={`0 0 ${OVERLAY_W} ${OVERLAY_H}`}>
        <Defs>
          <LinearGradient id="scannedGrad" x1="0" y1="0" x2="1" y2="1">
            <Stop offset="0" stopColor="#22c55e" stopOpacity="0.35" />
            <Stop offset="1" stopColor="#16a34a" stopOpacity="0.25" />
          </LinearGradient>
          <LinearGradient id="activeGrad" x1="0" y1="0" x2="1" y2="1">
            <Stop offset="0" stopColor="#3b82f6" stopOpacity="0.25" />
            <Stop offset="1" stopColor="#2563eb" stopOpacity="0.15" />
          </LinearGradient>
        </Defs>

        {/* Background frame */}
        <Rect
          x={0} y={0}
          width={OVERLAY_W} height={OVERLAY_H}
          rx={12} ry={12}
          fill="none"
          stroke="#ffffff"
          strokeWidth={2}
          strokeOpacity={0.3}
        />

        {/* Grid cells */}
        {cells.map((scanned, idx) => {
          const row = Math.floor(idx / cols);
          const col = idx % cols;
          const x = PADDING + col * cellW;
          const y = PADDING + row * cellH;
          const isMissing = !scanned && missingRegions.some(r => r.row === row && r.col === col);
          return (
            <Rect
              key={idx}
              x={x} y={y}
              width={cellW - 1} height={cellH - 1}
              rx={4} ry={4}
              fill={scanned ? 'url(#scannedGrad)' : isMissing ? 'url(#activeGrad)' : 'none'}
              stroke={scanned ? '#22c55e' : isMissing ? '#3b82f6' : 'rgba(255,255,255,0.08)'}
              strokeWidth={scanned ? 1.5 : 1}
              strokeOpacity={scanned ? 0.7 : isMissing ? 0.5 : 0.15}
            />
          );
        })}

        {/* Center crosshair */}
        <Circle cx={OVERLAY_W / 2} cy={OVERLAY_H / 2} r={24} fill="none" stroke="#ffffff" strokeWidth={1} strokeOpacity={0.25} />
        <Circle cx={OVERLAY_W / 2} cy={OVERLAY_H / 2} r={4} fill="#ffffff" fillOpacity={0.5} />
      </Svg>

      {/* Top-left: percentage */}
      <View style={[styles.badge, styles.topLeft]}>
        <Text style={styles.badgeText}>{coveragePercent}%</Text>
      </View>

      {/* Top-right: timer */}
      <View style={[styles.badge, styles.topRight]}>
        <Text style={styles.badgeText}>{formatTime(elapsedMs)}</Text>
      </View>

      {/* Bottom-center: guidance text */}
      {isScanning && (
        <View style={styles.guidanceBox}>
          <Text style={styles.guidanceText}>{guidance}</Text>
          {coveragePercent >= 90 && (
            <Text style={styles.holdText}>Hold steady for auto-stop…</Text>
          )}
        </View>
      )}

      {/* Auto-stop countdown indicator */}
      {isScanning && coveragePercent >= 90 && (
        <View style={styles.progressRingContainer}>
          <Svg width={48} height={48} viewBox="0 0 48 48">
            <Circle cx={24} cy={24} r={20} fill="none" stroke="rgba(255,255,255,0.2)" strokeWidth={3} />
            <Circle
              cx={24} cy={24} r={20}
              fill="none"
              stroke="#22c55e"
              strokeWidth={3}
              strokeLinecap="round"
              strokeDasharray={`${2 * Math.PI * 20}`}
              strokeDashoffset={`${2 * Math.PI * 20 * (1 - Math.min(elapsedMs / autoStopDelayMs, 1))}`}
              transform="rotate(-90 24 24)"
            />
          </Svg>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 0, left: 0, right: 0, bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badge: {
    position: 'absolute',
    backgroundColor: 'rgba(0,0,0,0.5)',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 8,
  },
  topLeft: { top: 12, left: 12 },
  topRight: { top: 12, right: 12 },
  badgeText: {
    color: '#ffffff',
    fontWeight: '700',
    fontSize: 14,
    fontVariant: ['tabular-nums'],
  },
  guidanceBox: {
    position: 'absolute',
    bottom: 80,
    backgroundColor: 'rgba(0,0,0,0.55)',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    alignItems: 'center',
  },
  guidanceText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '600',
  },
  holdText: {
    color: '#a7f3d0',
    fontSize: 11,
    marginTop: 2,
  },
  progressRingContainer: {
    position: 'absolute',
    bottom: 28,
    alignItems: 'center',
  },
});
