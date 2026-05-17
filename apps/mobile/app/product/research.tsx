import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, ActivityIndicator } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';

export default function ResearchScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [pc, setPc] = useState<any>(null);
  const [running, setRunning] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    const data = await api.getProductCase(id);
    setPc(data);
  }, [id, api]);

  useEffect(() => { load(); }, [load]);

  const onRun = async () => {
    if (!id) return;
    setRunning(true);
    try {
      await api.runResearch(id);
      await load();
    } catch (e) {
      console.warn(e);
    } finally {
      setRunning(false);
    }
  };

  if (!pc) return <View style={styles.center}><ActivityIndicator size="large" color="#2563eb" /></View>;

  const mr = pc.marketResearchResult;

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.heading}>Market Research</Text>

      {!mr && (
        <View style={styles.empty}>
          <Text style={styles.emptyText}>Research not run yet.</Text>
          <TouchableOpacity style={styles.btn} onPress={onRun} disabled={running}>
            <Text style={styles.btnText}>{running ? 'Running...' : 'Run Market Research'}</Text>
          </TouchableOpacity>
        </View>
      )}

      {mr && (
        <>
          <View style={styles.rangeCard}>
            <Text style={styles.rangeLabel}>Market Range</Text>
            <Text style={styles.rangeValue}>{mr.estimatedMarketLow?.amount?.toFixed?.(0) || mr.estimatedMarketLow} € – {mr.estimatedMarketHigh?.amount?.toFixed?.(0) || mr.estimatedMarketHigh} €</Text>
            <Text style={styles.rangeMid}>Most realistic: {mr.estimatedMarketMid?.amount?.toFixed?.(0) || mr.estimatedMarketMid} €</Text>
            <Text style={styles.confidence}>Confidence: {mr.confidence}</Text>
          </View>

          <Text style={styles.subHeading}>Comparable Listings</Text>
          {(mr.comparables || []).map((c: any, i: number) => (
            <View key={i} style={styles.compCard}>
              <Text style={styles.compTitle}>{c.title}</Text>
              <View style={styles.compRow}>
                <Text style={styles.compPlatform}>{c.platform}</Text>
                <Text style={styles.compPrice}>{c.price?.amount} €</Text>
              </View>
              <Text style={styles.compMeta}>{c.sold ? 'Sold' : 'Active'} · Relevance {(c.relevanceScore * 100).toFixed(0)}%</Text>
            </View>
          ))}

          {mr.warnings?.length > 0 && (
            <View style={styles.warnBox}>
              {mr.warnings.map((w: string, i: number) => (
                <Text key={i} style={styles.warnText}>⚠ {w}</Text>
              ))}
            </View>
          )}

          <TouchableOpacity style={styles.btn} onPress={onRun} disabled={running}>
            <Text style={styles.btnText}>{running ? 'Refreshing...' : 'Refresh Research'}</Text>
          </TouchableOpacity>

          <TouchableOpacity style={[styles.btn, { backgroundColor: '#16a34a' }]} onPress={() => router.push(`/product/pricing?id=${id}`)}>
            <Text style={styles.btnText}>Go to Pricing</Text>
          </TouchableOpacity>
        </>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  container: { padding: 16, backgroundColor: '#f8f9fa', flexGrow: 1 },
  heading: { fontSize: 18, fontWeight: '700', color: '#111827', marginBottom: 12 },
  empty: { alignItems: 'center', marginTop: 40 },
  emptyText: { color: '#6b7280', fontSize: 14, marginBottom: 16 },
  rangeCard: { backgroundColor: '#ffffff', borderRadius: 12, padding: 16, marginBottom: 14, borderWidth: 1, borderColor: '#f3f4f6', alignItems: 'center' },
  rangeLabel: { fontSize: 13, color: '#6b7280', marginBottom: 4 },
  rangeValue: { fontSize: 24, fontWeight: '800', color: '#111827' },
  rangeMid: { fontSize: 15, fontWeight: '600', color: '#2563eb', marginTop: 6 },
  confidence: { fontSize: 12, color: '#9ca3af', marginTop: 4 },
  subHeading: { fontSize: 15, fontWeight: '700', color: '#374151', marginBottom: 8 },
  compCard: { backgroundColor: '#ffffff', borderRadius: 10, padding: 12, marginBottom: 8, borderWidth: 1, borderColor: '#f3f4f6' },
  compTitle: { fontSize: 14, fontWeight: '600', color: '#111827', marginBottom: 4 },
  compRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 2 },
  compPlatform: { fontSize: 12, color: '#6b7280' },
  compPrice: { fontSize: 14, fontWeight: '700', color: '#111827' },
  compMeta: { fontSize: 12, color: '#9ca3af' },
  warnBox: { backgroundColor: '#fef3c7', borderRadius: 8, padding: 10, marginBottom: 12 },
  warnText: { fontSize: 13, color: '#92400e', marginBottom: 2 },
  btn: { backgroundColor: '#2563eb', paddingVertical: 14, borderRadius: 10, alignItems: 'center', marginBottom: 10 },
  btnText: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
});
