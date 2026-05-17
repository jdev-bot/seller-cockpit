import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Alert, ActivityIndicator } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';

export default function ProductCaseDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [pc, setPc] = useState<any>(null);

  const load = useCallback(async () => {
    if (!id) return;
    try {
      const data = await api.getProductCase(id);
      setPc(data);
    } catch (e) {
      console.warn(e);
    }
  }, [id, api]);

  useEffect(() => {
    load();
  }, [load]);

  const onDelete = () => {
    Alert.alert('Delete product case?', 'This cannot be undone.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete', style: 'destructive', onPress: async () => {
          await api.deleteProductCase(id!);
          router.replace('/');
        }
      }
    ]);
  };

  if (!pc) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#2563eb" />
      </View>
    );
  }

  const steps: { label: string; route: string; done: boolean; disabled: boolean }[] = [
    { label: 'Capture Media', route: `/product/capture?id=${id}`, done: pc.status !== 'CAPTURED' && pc.status !== 'PROCESSING_MEDIA', disabled: false },
    { label: 'Review Product Facts', route: `/product/review?id=${id}`, done: !!pc.productFacts?.userConfirmed, disabled: !pc.productFacts },
    { label: 'Market Research', route: `/product/research?id=${id}`, done: !!pc.marketResearchResult, disabled: pc.status === 'NEEDS_USER_INFO' },
    { label: 'Pricing', route: `/product/pricing?id=${id}`, done: !!pc.pricingRecommendation, disabled: !pc.marketResearchResult },
    { label: 'Listings', route: `/product/listings?id=${id}`, done: pc.status === 'PUBLISHED' || pc.status === 'PARTIALLY_PUBLISHED', disabled: !pc.pricingRecommendation },
    { label: 'Tracking', route: `/product/tracking?id=${id}`, done: false, disabled: pc.status !== 'PUBLISHED' && pc.status !== 'PARTIALLY_PUBLISHED' },
  ];

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>{pc.title || pc.productFacts?.title || 'Product Case'}</Text>
        <Text style={styles.mode}>{pc.sellerMode.replace(/_/g, ' ')}</Text>
        <View style={styles.statusBadge}>
          <Text style={styles.statusText}>{pc.status.replace(/_/g, ' ')}</Text>
        </View>
      </View>

      <Text style={styles.section}>Steps</Text>
      {steps.map((step, idx) => (
        <TouchableOpacity
          key={idx}
          style={[styles.stepCard, step.disabled && styles.stepCardDisabled, step.done && styles.stepCardDone]}
          onPress={() => !step.disabled && router.push(step.route)}
          disabled={step.disabled}
        >
          <Text style={styles.stepNum}>{idx + 1}</Text>
          <Text style={styles.stepLabel}>{step.label}</Text>
          <Text style={styles.stepStatus}>{step.done ? 'Done' : step.disabled ? 'Locked' : 'Open'}</Text>
        </TouchableOpacity>
      ))}

      <TouchableOpacity style={styles.deleteBtn} onPress={onDelete}>
        <Text style={styles.deleteText}>Delete Product Case</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  container: { padding: 16, backgroundColor: '#f8f9fa', flexGrow: 1 },
  header: { backgroundColor: '#ffffff', padding: 16, borderRadius: 12, marginBottom: 14, borderWidth: 1, borderColor: '#f3f4f6' },
  title: { fontSize: 18, fontWeight: '700', color: '#111827', marginBottom: 4 },
  mode: { fontSize: 13, color: '#6b7280', marginBottom: 8, textTransform: 'capitalize' },
  statusBadge: { alignSelf: 'flex-start', backgroundColor: '#2563eb', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 12 },
  statusText: { color: '#ffffff', fontSize: 11, fontWeight: '700' },
  section: { fontSize: 16, fontWeight: '700', color: '#111827', marginBottom: 10, marginTop: 4 },
  stepCard: { flexDirection: 'row', alignItems: 'center', padding: 14, backgroundColor: '#ffffff', borderRadius: 10, marginBottom: 8, borderWidth: 1, borderColor: '#e5e7eb' },
  stepCardDisabled: { opacity: 0.5, backgroundColor: '#f3f4f6' },
  stepCardDone: { borderColor: '#22c55e', backgroundColor: '#f0fdf4' },
  stepNum: { width: 28, height: 28, borderRadius: 14, backgroundColor: '#2563eb', color: '#ffffff', textAlign: 'center', lineHeight: 28, fontWeight: '700', marginRight: 12 },
  stepLabel: { flex: 1, fontSize: 15, fontWeight: '600', color: '#111827' },
  stepStatus: { fontSize: 12, color: '#6b7280' },
  deleteBtn: { marginTop: 20, alignSelf: 'center', paddingHorizontal: 20, paddingVertical: 10 },
  deleteText: { color: '#ef4444', fontWeight: '600', fontSize: 14 },
});
