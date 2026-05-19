import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, TextInput, TouchableOpacity, ScrollView, RefreshControl } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';
import { SkeletonList } from '../components/Skeleton';
import { ErrorRetry } from '../components/ErrorRetry';
import { OfflineBanner } from '../components/OfflineBanner';

export default function ReviewFactsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [pc, setPc] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    if (!id) return;
    try {
      setError(null);
      setLoading(true);
      const data = await api.getProductCase(id);
      setPc(data);
      const init: Record<string, string> = {};
      (data.missingQuestions || []).forEach((q: string, i: number) => {
        init[`q${i}`] = '';
      });
      setAnswers(init);
    } catch (e: any) {
      console.warn(e);
      setError(e.message || 'Failed to load product facts');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [id, api]);

  useEffect(() => { load(); }, [load]);

  const onSave = async () => {
    if (!id) return;
    setSaving(true);
    try {
      await api.submitAnswers(id, answers);
      router.replace(`/product/${id}`);
    } catch (e: any) {
      console.warn(e);
      setError(e.message || 'Failed to save answers');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.container}>
        <OfflineBanner />
        <SkeletonList count={6} />
      </View>
    );
  }

  if (error && !pc) {
    return (
      <View style={styles.container}>
        <OfflineBanner />
        <ErrorRetry message={error} onRetry={load} />
      </View>
    );
  }

  if (!pc) {
    return (
      <View style={styles.container}>
        <OfflineBanner />
        <ErrorRetry message="Product not found" onRetry={load} />
      </View>
    );
  }

  const facts = pc.productFacts;
  const cond = pc.conditionAssessment;

  return (
    <ScrollView
      contentContainerStyle={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} />}
    >
      <OfflineBanner />
      <Text style={styles.heading}>Detected Product</Text>
      {facts && (
        <View style={styles.card}>
          <Row label="Title" value={facts.title} />
          <Row label="Brand" value={facts.brand} />
          <Row label="Model" value={facts.model} />
          <Row label="Category" value={facts.category} />
          <Row label="Variant" value={facts.variant} />
          <Row label="Color" value={facts.color} />
          <Row label="Confidence" value={`${(facts.confidence * 100).toFixed(0)}%`} />
          {facts.accessories?.length > 0 && (
            <Row label="Accessories" value={facts.accessories.join(', ')} />
          )}
        </View>
      )}
      {!facts && <Text style={styles.none}>No facts detected yet.</Text>}

      <Text style={styles.heading}>Condition</Text>
      {cond && (
        <View style={styles.card}>
          <Row label="Condition" value={cond.condition.replace(/_/g, ' ')} />
          <Row label="Visible Defects" value={cond.visibleDefects.join(', ') || 'None'} />
          <Row label="Functionality" value={cond.functionalityConfirmed == null ? 'Not confirmed' : cond.functionalityConfirmed ? 'Works' : 'Not working'} />
          <Row label="Confidence" value={`${(cond.confidence * 100).toFixed(0)}%`} />
          {cond.missingInformation?.length > 0 && (
            <Row label="Missing Info" value={cond.missingInformation.join(', ')} />
          )}
        </View>
      )}
      {!cond && <Text style={styles.none}>No condition assessment yet.</Text>}

      <Text style={styles.heading}>Missing Information</Text>
      {pc.missingQuestions.length === 0 && <Text style={styles.allClear}>All clear! No questions left.</Text>}
      {pc.missingQuestions.map((q: string, i: number) => (
        <View key={i} style={styles.qCard}>
          <Text style={styles.qText}>{q}</Text>
          <TextInput
            style={styles.input}
            placeholder="Your answer..."
            value={answers[`q${i}`] || ''}
            onChangeText={(text) => setAnswers((prev) => ({ ...prev, [`q${i}`]: text }))}
            editable={!saving}
          />
        </View>
      ))}

      {error && (
        <View style={styles.errorCard}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      )}

      <TouchableOpacity style={[styles.btn, saving && styles.btnDisabled]} onPress={onSave} disabled={saving}>
        <Text style={styles.btnText}>{saving ? 'Saving...' : 'Confirm & Continue'}</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.secondaryBtn} onPress={() => router.replace(`/product/${id}`)} disabled={saving}>
        <Text style={styles.secondaryBtnText}>Back to Product</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

function Row({ label, value }: { label: string; value?: string }) {
  if (!value) return null;
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, backgroundColor: '#f8f9fa', flexGrow: 1 },
  heading: { fontSize: 16, fontWeight: '700', color: '#111827', marginTop: 14, marginBottom: 8 },
  card: { backgroundColor: '#ffffff', borderRadius: 10, padding: 12, borderWidth: 1, borderColor: '#f3f4f6' },
  row: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: '#f3f4f6' },
  rowLabel: { fontSize: 13, color: '#6b7280', fontWeight: '500' },
  rowValue: { fontSize: 13, color: '#111827', fontWeight: '600', maxWidth: '60%', textAlign: 'right' },
  none: { fontSize: 14, color: '#6b7280', fontStyle: 'italic', marginBottom: 12 },
  allClear: { fontSize: 14, color: '#059669', fontWeight: '600', marginBottom: 12 },
  qCard: { backgroundColor: '#ffffff', borderRadius: 10, padding: 12, marginBottom: 8, borderWidth: 1, borderColor: '#f3f4f6' },
  qText: { fontSize: 14, color: '#374151', fontWeight: '600', marginBottom: 6 },
  input: { borderWidth: 1, borderColor: '#e5e7eb', borderRadius: 8, paddingHorizontal: 10, paddingVertical: 8, fontSize: 14, color: '#111827', backgroundColor: '#fafafa' },
  errorCard: { backgroundColor: '#fef2f2', borderRadius: 10, padding: 12, marginBottom: 12, borderWidth: 1, borderColor: '#fecaca' },
  errorText: { color: '#dc2626', fontSize: 13, fontWeight: '500' },
  btn: { marginTop: 16, backgroundColor: '#2563eb', paddingVertical: 14, borderRadius: 10, alignItems: 'center' },
  btnDisabled: { opacity: 0.6 },
  btnText: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
  secondaryBtn: { marginTop: 10, paddingVertical: 14, borderRadius: 10, alignItems: 'center', backgroundColor: '#f3f4f6' },
  secondaryBtnText: { color: '#374151', fontWeight: '600', fontSize: 15 },
});
