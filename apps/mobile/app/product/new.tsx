import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, TextInput, ScrollView } from 'react-native';
import { useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';

const MODES = [
  { key: 'PRIVATE_DECLUTTERING', label: 'Private Decluttering', desc: 'I own this item and just want to sell it.' },
  { key: 'PRIVATE_RESELLING', label: 'Private Reselling', desc: 'I bought this item to resell or want to consider purchase price.' },
  { key: 'PROFESSIONAL', label: 'Professional Selling', desc: 'I sell commercially and need tax/cost/margin calculation.' },
];

export default function NewProductScreen() {
  const router = useRouter();
  const api = useApi();
  const [selectedMode, setSelectedMode] = useState('PRIVATE_DECLUTTERING');
  const [title, setTitle] = useState('');

  const onContinue = async () => {
    try {
      const pc = await api.createProductCase(selectedMode, title || undefined);
      router.push(`/product/${pc.id}`);
    } catch (e) {
      console.warn(e);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.step}>Step 1 of 2</Text>
      <Text style={styles.heading}>What are you doing with this item?</Text>

      {MODES.map((m) => (
        <TouchableOpacity
          key={m.key}
          style={[styles.modeCard, selectedMode === m.key && styles.modeCardActive]}
          onPress={() => setSelectedMode(m.key)}
        >
          <View style={[styles.radio, selectedMode === m.key && styles.radioActive]}>
            {selectedMode === m.key && <View style={styles.radioInner} />}
          </View>
          <View style={styles.modeText}>
            <Text style={styles.modeLabel}>{m.label}</Text>
            <Text style={styles.modeDesc}>{m.desc}</Text>
          </View>
        </TouchableOpacity>
      ))}

      <Text style={styles.label}>Product name (optional)</Text>
      <TextInput
        style={styles.input}
        placeholder="e.g., Apple Magic Keyboard"
        value={title}
        onChangeText={setTitle}
      />

      <TouchableOpacity style={styles.btn} onPress={onContinue}>
        <Text style={styles.btnText}>Continue to Media Capture</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, backgroundColor: '#ffffff', flexGrow: 1 },
  step: { fontSize: 12, color: '#6b7280', marginBottom: 4 },
  heading: { fontSize: 18, fontWeight: '700', color: '#111827', marginBottom: 16 },
  modeCard: { flexDirection: 'row', alignItems: 'flex-start', padding: 14, borderRadius: 10, borderWidth: 1.5, borderColor: '#e5e7eb', marginBottom: 10, backgroundColor: '#ffffff' },
  modeCardActive: { borderColor: '#2563eb', backgroundColor: '#eff6ff' },
  radio: { width: 20, height: 20, borderRadius: 10, borderWidth: 2, borderColor: '#d1d5db', marginRight: 12, marginTop: 2, alignItems: 'center', justifyContent: 'center' },
  radioActive: { borderColor: '#2563eb' },
  radioInner: { width: 10, height: 10, borderRadius: 5, backgroundColor: '#2563eb' },
  modeText: { flex: 1 },
  modeLabel: { fontSize: 15, fontWeight: '700', color: '#111827', marginBottom: 2 },
  modeDesc: { fontSize: 13, color: '#6b7280', lineHeight: 18 },
  label: { fontSize: 14, fontWeight: '600', color: '#374151', marginTop: 16, marginBottom: 6 },
  input: { borderWidth: 1, borderColor: '#e5e7eb', borderRadius: 8, paddingHorizontal: 12, paddingVertical: 10, fontSize: 15, color: '#111827', backgroundColor: '#fafafa' },
  btn: { marginTop: 20, backgroundColor: '#2563eb', paddingVertical: 14, borderRadius: 10, alignItems: 'center' },
  btnText: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
});
