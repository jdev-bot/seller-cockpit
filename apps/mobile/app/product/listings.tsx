import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, ActivityIndicator, Alert, TextInput } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';

export default function ListingsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [drafts, setDrafts] = useState<any[]>([]);
  const [generating, setGenerating] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    try {
      const data = await api.getListingDrafts(id);
      setDrafts(data);
    } catch (e) {
      console.warn(e);
    }
  }, [id, api]);

  useEffect(() => { load(); }, [load]);

  const onGenerate = async () => {
    if (!id) return;
    setGenerating(true);
    try {
      await api.generateListings(id);
      await load();
    } catch (e) {
      console.warn(e);
    } finally {
      setGenerating(false);
    }
  };

  const onPublish = async (draft: any) => {
    if (!id) return;
    try {
      await api.publishListing(draft.id, draft.platform);
      Alert.alert('Published', `${draft.platform} listing marked as publishing.`);
      router.push(`/product/tracking?id=${id}`);
    } catch (e: any) {
      Alert.alert('Publish failed', e.message || 'Unknown error');
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.heading}>Listing Drafts</Text>

      {drafts.length === 0 && (
        <View style={styles.empty}>
          <Text style={styles.emptyText}>No drafts yet.</Text>
          <TouchableOpacity style={styles.btn} onPress={onGenerate} disabled={generating}>
            <Text style={styles.btnText}>{generating ? 'Generating...' : 'Generate Drafts'}</Text>
          </TouchableOpacity>
        </View>
      )}

      {drafts.map((draft) => (
        <View key={draft.id} style={styles.draftCard}>
          <View style={styles.draftHeader}>
            <Text style={styles.draftPlatform}>{draft.platform}</Text>
            <View style={[styles.badge, draft.readyToPublish ? styles.badgeReady : styles.badgeBlocked]}>
              <Text style={styles.badgeText}>{draft.readyToPublish ? 'Ready' : 'Blocked'}</Text>
            </View>
          </View>

          <Text style={styles.draftTitle}>{draft.title}</Text>
          <Text style={styles.draftPrice}>{draft.price?.amount?.toFixed?.(2) || draft.price} €</Text>

          <Text style={styles.label}>Description preview</Text>
          <Text style={styles.draftDesc} numberOfLines={4}>{draft.description}</Text>

          {draft.warnings?.length > 0 && (
            <View style={styles.warnBox}>
              {draft.warnings.map((w: string, i: number) => (
                <Text key={i} style={styles.warnText}>⚠ {w}</Text>
              ))}
            </View>
          )}

          <View style={styles.actions}>
            <TouchableOpacity style={[styles.actionBtn, styles.publishBtn]} onPress={() => onPublish(draft)}>
              <Text style={styles.actionBtnText}>Publish</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.actionBtn, styles.copyBtn]} onPress={() => {
              // In real app, use Clipboard API
              Alert.alert('Copied', 'Listing content copied to clipboard (mock)');
            }}>
              <Text style={styles.actionBtnText}>Copy</Text>
            </TouchableOpacity>
          </View>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  container: { padding: 16, backgroundColor: '#f8f9fa', flexGrow: 1 },
  heading: { fontSize: 18, fontWeight: '700', color: '#111827', marginBottom: 12 },
  empty: { alignItems: 'center', marginTop: 40 },
  emptyText: { color: '#6b7280', fontSize: 14, marginBottom: 16 },
  draftCard: { backgroundColor: '#ffffff', borderRadius: 12, padding: 14, marginBottom: 12, borderWidth: 1, borderColor: '#f3f4f6' },
  draftHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  draftPlatform: { fontSize: 14, fontWeight: '700', color: '#2563eb', textTransform: 'uppercase' },
  badge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10 },
  badgeReady: { backgroundColor: '#22c55e' },
  badgeBlocked: { backgroundColor: '#ef4444' },
  badgeText: { color: '#ffffff', fontSize: 10, fontWeight: '700' },
  draftTitle: { fontSize: 16, fontWeight: '700', color: '#111827', marginBottom: 2 },
  draftPrice: { fontSize: 18, fontWeight: '800', color: '#111827', marginBottom: 8 },
  label: { fontSize: 12, fontWeight: '600', color: '#6b7280', marginBottom: 4 },
  draftDesc: { fontSize: 13, color: '#374151', lineHeight: 18, marginBottom: 8 },
  warnBox: { backgroundColor: '#fef3c7', borderRadius: 8, padding: 8, marginBottom: 10 },
  warnText: { fontSize: 12, color: '#92400e', marginBottom: 2 },
  actions: { flexDirection: 'row', gap: 10 },
  actionBtn: { flex: 1, paddingVertical: 10, borderRadius: 8, alignItems: 'center' },
  publishBtn: { backgroundColor: '#2563eb' },
  copyBtn: { backgroundColor: '#6b7280' },
  actionBtnText: { color: '#ffffff', fontWeight: '700', fontSize: 14 },
  btn: { backgroundColor: '#2563eb', paddingVertical: 14, borderRadius: 10, alignItems: 'center', marginBottom: 10 },
  btnText: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
});
