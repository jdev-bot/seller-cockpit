import React from 'react';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity, Dimensions,
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import Svg, { Rect, Text as SvgText } from 'react-native-svg';
import { useScanCoverage } from '../hooks/useScanCoverage';

const { width: SCREEN_W } = Dimensions.get('window');
const CHART_W = SCREEN_W - 32;
const CHART_H = CHART_W * 0.6;
const PADDING = 8;

export default function ScanCoverageScreen() {
  const { productCaseId } = useLocalSearchParams<{ productCaseId: string }>();
  const router = useRouter();

  const {
    coverageGrid,
    coveragePercent,
    missingRegions,
    isScanComplete,
    resetCoverage,
    startScanning,
    stopScanning,
    isScanning,
  } = useScanCoverage(95, 2000);

  const { rows, cols, cells } = coverageGrid;
  const cellW = (CHART_W - PADDING * 2) / cols;
  const cellH = (CHART_H - PADDING * 2) / rows;

  const handleRetake = () => {
    resetCoverage();
    router.push(`/product/capture?id=${productCaseId}`);
  };

  const handleContinue = () => {
    router.push(`/product/${productCaseId}`);
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.title}>Scan Coverage</Text>
      <Text style={styles.subtitle}>
        {isScanComplete
          ? 'Product fully scanned! Ready to process.'
          : `${coveragePercent}% scanned — ${missingRegions.length} regions remaining`}
      </Text>

      {/* Coverage chart */}
      <View style={styles.chartCard}>
        <Svg width={CHART_W} height={CHART_H} viewBox={`0 0 ${CHART_W} ${CHART_H}`}>
          {cells.map((scanned, idx) => {
            const row = Math.floor(idx / cols);
            const col = idx % cols;
            const x = PADDING + col * cellW;
            const y = PADDING + row * cellH;
            const isMissing = !scanned && missingRegions.some(r => r.row === row && r.col === col);
            return (
              <>
                <Rect
                  key={`r-${idx}`}
                  x={x} y={y}
                  width={cellW - 2} height={cellH - 2}
                  rx={6} ry={6}
                  fill={scanned ? '#dcfce7' : isMissing ? '#fef3c7' : '#f3f4f6'}
                  stroke={scanned ? '#22c55e' : isMissing ? '#f59e0b' : '#e5e7eb'}
                  strokeWidth={scanned ? 2 : 1}
                />
                {isMissing && (
                  <SvgText
                    x={x + cellW / 2 - 1}
                    y={y + cellH / 2 + 4}
                    fontSize={10}
                    fill="#92400e"
                    textAnchor="middle"
                  >
                    !
                  </SvgText>
                )}
              </>
            );
          })}
        </Svg>
      </View>

      {/* Missing regions list */}
      {missingRegions.length > 0 && (
        <View style={styles.missingCard}>
          <Text style={styles.missingTitle}>Missing angles</Text>
          {missingRegions.map((r, i) => (
            <View key={i} style={styles.missingRow}>
              <Text style={styles.missingDot}>●</Text>
              <Text style={styles.missingLabel}>{r.label}</Text>
            </View>
          ))}
        </View>
      )}

      {/* Summary stats */}
      <View style={styles.statsRow}>
        <View style={styles.statBox}>
          <Text style={styles.statValue}>{coveragePercent}%</Text>
          <Text style={styles.statLabel}>Coverage</Text>
        </View>
        <View style={styles.statBox}>
          <Text style={styles.statValue}>{missingRegions.length}</Text>
          <Text style={styles.statLabel}>Missing</Text>
        </View>
        <View style={styles.statBox}>
          <Text style={[styles.statValue, isScanComplete && styles.completeValue]}>
            {isScanComplete ? '✓' : '…'}
          </Text>
          <Text style={styles.statLabel}>Status</Text>
        </View>
      </View>

      {/* Actions */}
      <View style={styles.actions}>
        <TouchableOpacity style={[styles.actionBtn, styles.retakeBtn]} onPress={handleRetake}>
          <Text style={styles.retakeText}>🔁 Rescan Product</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.actionBtn, styles.continueBtn, !isScanComplete && styles.continueBtnDisabled]}
          onPress={handleContinue}
          disabled={!isScanComplete}
        >
          <Text style={styles.continueText}>Continue →</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8f9fa' },
  content: { padding: 16, paddingBottom: 40 },
  title: { fontSize: 22, fontWeight: 'bold', color: '#111827', marginBottom: 4 },
  subtitle: { fontSize: 14, color: '#6b7280', marginBottom: 16 },
  chartCard: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 12,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#e5e7eb',
    alignItems: 'center',
  },
  missingCard: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  missingTitle: { fontSize: 14, fontWeight: '700', color: '#111827', marginBottom: 8 },
  missingRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 4 },
  missingDot: { color: '#f59e0b', fontSize: 10, marginRight: 8 },
  missingLabel: { fontSize: 14, color: '#4b5563' },
  statsRow: { flexDirection: 'row', gap: 12, marginBottom: 20 },
  statBox: {
    flex: 1,
    backgroundColor: '#ffffff',
    borderRadius: 10,
    padding: 12,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  statValue: { fontSize: 20, fontWeight: 'bold', color: '#111827' },
  statLabel: { fontSize: 12, color: '#6b7280', marginTop: 2 },
  completeValue: { color: '#22c55e' },
  actions: { gap: 10 },
  actionBtn: { paddingVertical: 14, borderRadius: 10, alignItems: 'center' },
  retakeBtn: { backgroundColor: '#ffffff', borderWidth: 1, borderColor: '#d1d5db' },
  retakeText: { color: '#374151', fontWeight: '600', fontSize: 15 },
  continueBtn: { backgroundColor: '#2563eb' },
  continueBtnDisabled: { backgroundColor: '#9ca3af' },
  continueText: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
});
