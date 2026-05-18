import React, { useState, useEffect, useCallback } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, Alert,
  TextInput, Clipboard, Linking
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';
import { SkeletonList } from '../components/Skeleton';
import { EmptyState } from '../components/EmptyState';
import { ErrorRetry } from '../components/ErrorRetry';
import { OfflineBanner } from '../components/OfflineBanner';
import { LoadingButton } from '../components/LoadingButton';

export default function ListingsScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [drafts, setDrafts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingDraftId, setEditingDraftId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editDesc, setEditDesc] = useState('');
  const [editPrice, setEditPrice] = useState('');

  const load = useCallback(async () => {
    if (!id) return;
    try {
      setError(null);
      setLoading(true);
      const data = await api.getListingDrafts(id);
      setDrafts(data);
    } catch (e: any) {
      console.warn(e);
      setError(e.message || 'Failed to load drafts');
    } finally {
      setLoading(false);
    }
  }, [id, api]);

  useEffect(() => { load(); }, [load]);

  const onGenerate = async () => {
    if (!id) return;
    setGenerating(true);
    try {
      await api.generateListings(id);
      await load();
    } catch (e: any) {
      Alert.alert('Generation failed', e.message || 'Could not generate drafts');
    } finally {
      setGenerating(false);
    }
  };

  const onPublish = async (draft: any) => {
    if (!id) return;
    if (draft.platform === 'KLEINANZEIGEN') {
      router.push(`/product/assisted-publish?id=${id}&draftId=${draft.id}`);
      return;
    }
    if (draft.platform === 'EBAY') {
      router.push(`/product/ebay-publish?id=${id}&draftId=${draft.id}`);
      return;
    }
  };

  const onCopyListing = (draft: any) => {
    const text = `${draft.title}\n\n${draft.description}\n\nPreis: ${draft.price?.amount?.toFixed?.(2) || draft.price} €`;
    Clipboard.setString(text);
    Alert.alert('Copied', 'Listing text copied to clipboard');
  };

  const onOpenEditor = (draft: any) => {
    setEditingDraftId(draft.id);
    setEditTitle(draft.title);
    setEditDesc(draft.description);
    setEditPrice(draft.price?.amount?.toFixed?.(2) || '');
  };

  const onSaveEdit = () => {
    setDrafts(prev => prev.map(d => d.id === editingDraftId ? { ...d, title: editTitle, description: editDesc, price: { ...d.price, amount: parseFloat(editPrice) || d.price.amount } } : d));
    setEditingDraftId(null);
    Alert.alert('Saved', 'Draft updated locally. Backend sync not yet wired.');
  };

  return (
    <View style={styles.container}>
      <OfflineBanner />
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.heading}>Listing Drafts</Text>

        {loading ? (
          <SkeletonList count={3} />
        ) : error ? (
          <ErrorRetry message={error} onRetry={load} />
        ) : drafts.length === 0 ? (
          <EmptyState
            icon="📝"
            title="No drafts yet"
            subtitle="Generate AI-powered drafts for this product."
            actionLabel="Generate Drafts"
            onAction={onGenerate}
          />
        ) : (
          <>
            {drafts.map((draft) => (
              <View key={draft.id} style={styles.draftCard}>
                <View style={styles.draftHeader}>
                  <Text style={styles.draftPlatform}>{draft.platform}</Text>
                  <View style={[styles.badge, draft.readyToPublish ? styles.badgeReady : styles.badgeBlocked]}>
                    <Text style={styles.badgeText}>{draft.readyToPublish ? 'Ready' : 'Blocked'}</Text>
                  </View>
                </View>

                {editingDraftId === draft.id ? (
                  <>
                    <Text style={styles.label}>Title</Text>
                    <TextInput style={styles.input} value={editTitle} onChangeText={setEditTitle} />
                    <Text style={styles.label}>Description</Text>
                    <TextInput style={[styles.input, { height: 80 }]} multiline value={editDesc} onChangeText={setEditDesc} />
                    <Text style={styles.label}>Price (EUR)</Text>
                    <TextInput style={styles.input} keyboardType="decimal-pad" value={editPrice} onChangeText={setEditPrice} />
                    <View style={styles.actions}>
                      <LoadingButton label="Save" onPress={onSaveEdit} variant="primary" />
                      <LoadingButton label="Cancel" onPress={() => setEditingDraftId(null)} variant="secondary" />
                    </View>
                  </>
                ) : (
                  <>
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
                      <LoadingButton
                        label={draft.platform === 'KLEINANZEIGEN' ? 'Assist' : 'Publish'}
                        onPress={() => onPublish(draft)}
                        variant="primary"
                      />
                      <LoadingButton label="Copy" onPress={() => onCopyListing(draft)} variant="secondary" />
                      <LoadingButton label="Edit" onPress={() => onOpenEditor(draft)} variant="secondary" />
                    </View>
                  </>
                )}
              </View>
            ))}

            <LoadingButton
              label={generating ? 'Generating...' : 'Regenerate Drafts'}
              onPress={onGenerate}
              loading={generating}
              variant="secondary"
              style={styles.regenBtn}
            />
          </>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8f9fa' },
  scroll: { padding: 16, flexGrow: 1 },
  heading: { fontSize: 18, fontWeight: '700', color: '#111827', marginBottom: 12 },
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
  input: { borderWidth: 1, borderColor: '#e5e7eb', borderRadius: 8, padding: 10, fontSize: 14, color: '#111827', backgroundColor: '#fafafa', marginBottom: 8 },
  regenBtn: { marginTop: 8 },
});
