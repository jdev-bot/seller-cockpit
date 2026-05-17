import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, TextInput, TouchableOpacity, ScrollView, Switch, ActivityIndicator } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';

export default function ReviewFactsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [pc, setPc] = useState<any>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    if (!id) return;
    const data = await api.getProductCase(id);
    setPc(data);
    const init: Record<string, string> = {};
    data.missingQuestions.forEach((q: string, i: number) => {
      init[`q${i}`] = '';
    });
    setAnswers(init);
  }, [id, api]);

  useEffect(() => { load(); }, [load]);

  const onSave = async () => {
    if (!id) return;
    await api.answerQuestions(id, answers);
    router.replace(`/product/${id}`);
  };

  if (!pc) return <View style={styles.center}><ActivityIndicator size="large" color="#2563eb" /></View>;

  const facts = pc.productFacts;
  const cond = pc.conditionAssessment;

  return (
    <ScrollView contentContainerStyle={styles.container}>
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
        </View>
      )}

      <Text style={styles.heading}>Condition</Text>
      {cond && (
        <View style={styles.card}>
          <Row label="Condition" value={cond.condition.replace(/_/g, ' ')} />
          <Row label="Visible Defects" value={cond.visibleDefects.join(', ') || 'None'} />
          <Row label="Functionality" value={cond.functionalityConfirmed == null ? 'Not confirmed' : cond.functionalityConfirmed ? 'Works' : 'Not working'} />
          <Row label="Confidence" value={`${(cond.confidence * 100).toFixed(0)}%`} />
        </View>
      )}

      <Text style={styles.heading}>Missing Information</Text>
      {pc.missingQuestions.length === 0 && <Text style={styles.none}>All clear!</Text>}
      {pc.missingQuestions.map((q: string, i: number) => (
        <View key={i} style={styles.qCard}>
          <Text style={styles.qText}>{q}</Text>
          <TextInput
            style={styles.input}
            placeholder="Your answer..."
            value={answers[`q${i}`] || ''}
            onChangeText={(text) => setAnswers((prev) => ({ ...prev, [`q${i}`]: text }))}
          />
        </View>
      ))}

      <TouchableOpacity style={styles.btn} onPress={onSave}>
        <Text style={styles.btnText}>Confirm & Continue</Text>
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
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  container: { padding: 16, backgroundColor: '#f8f9fa', flexGrow: 1 },
  heading: { fontSize: 16, fontWeight: '700', color: '#111827', marginTop: 14, marginBottom: 8 },
  card: { backgroundColor: '#ffffff', borderRadius: 10, padding: 12, borderWidth: 1, borderColor: '#f3f4f6' },
  row: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: '#f3f4f6' },
  rowLabel: { fontSize: 13, color: '#6b7280', fontWeight: '500' },
  rowValue: { fontSize: 13, color: '#111827', fontWeight: '600', maxWidth: '60%', textAlign: 'right' },
  none: { fontSize: 14, color: '#6b7280', fontStyle: 'italic', marginBottom: 12 },
  qCard: { backgroundColor: '#ffffff', borderRadius: 10, padding: 12, marginBottom: 8, borderWidth: 1, borderColor: '#f3f4f6' },
  qText: { fontSize: 14, color: '#374151', fontWeight: '600', marginBottom: 6 },
  input: { borderWidth: 1, borderColor: '#e5e7eb', borderRadius: 8, paddingHorizontal: 10, paddingVertical: 8, fontSize: 14, color: '#111827', backgroundColor: '#fafafa' },
  btn: { marginTop: 16, backgroundColor: '#2563eb', paddingVertical: 14, borderRadius: 10, alignItems: 'center' },
  btnText: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
});
