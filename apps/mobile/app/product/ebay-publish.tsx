import React, { useState, useEffect } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, Alert, ActivityIndicator, Linking
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';

export default function EbayPublishScreen() {
  const { id, draftId } = useLocalSearchParams<{ id: string; draftId: string }>();
  const router = useRouter();
  const api = useApi();
  const [loading, setLoading] = useState(true);
  const [publishing, setPublishing] = useState(false);
  const [draft, setDraft] = useState<any>(null);
  const [ebayConnected, setEbayConnected] = useState(false);
  const [connections, setConnections] = useState<any[]>([]);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    load();
  }, [id, draftId]);

  const load = async () => {
    try {
      setLoading(true);
      // Check eBay connection
      const cons = await api.listConnections();
      setConnections(cons);
      const ebayConn = cons.find((c: any) => c.platform === 'EBAY');
      setEbayConnected(!!ebayConn?.connected);

      // Get draft details
      if (draftId) {
        const listings = await api.getListings(id);
        const d = listings.find((l: any) => l.id === draftId && l.platform === 'EBAY');
        if (d) setDraft(d);
      }
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const handleConnect = async () => {
    try {
      const { url } = await api.getEbayConnectUrl();
      const supported = await Linking.canOpenURL(url);
      if (supported) {
        await Linking.openURL(url);
      } else {
        Alert.alert('Error', 'Cannot open eBay OAuth URL');
      }
    } catch (e: any) {
      Alert.alert('Error', e.message || 'Failed to get connect URL');
    }
  };

  const handlePublish = async () => {
    if (!draftId) return;
    try {
      setPublishing(true);
      setError(null);
      const res = await api.publishEbay(draftId);
      setResult(res);
    } catch (e: any) {
      setError(e.message || 'Publishing failed');
    } finally {
      setPublishing(false);
    }
  };

  const handleOpenEbay = () => {
    if (result?.externalUrl) {
      Linking.openURL(result.externalUrl);
    }
  };

  if (loading) {
    return (
      <View style={[styles.container, styles.center]}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  // Not connected view
  if (!ebayConnected) {
    return (
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <Text style={styles.header}>Publish to eBay</Text>

        <View style={styles.card}>
          <View style={styles.iconBox}>
            <Text style={styles.iconText}>eBay</Text>
          </View>
          <Text style={styles.cardTitle}>eBay Account Not Connected</Text>
          <Text style={styles.cardDesc}>
            To publish listings directly to eBay, you need to connect your eBay seller account and authorize this app.
          </Text>
          <TouchableOpacity style={styles.primaryBtn} onPress={handleConnect}>
            <Text style={styles.primaryBtnText}>Connect eBay Account</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.secondaryBtn} onPress={() => router.back()}>
            <Text style={styles.secondaryBtnText}>Cancel</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    );
  }

  // Success result view
  if (result) {
    return (
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <Text style={styles.header}>Published! ✅</Text>

        <View style={[styles.card, styles.successCard]}>
          <Text style={styles.cardTitle}>eBay Listing Created</Text>
          <Text style={styles.cardDesc}>
            Your listing has been published to eBay successfully.
          </Text>

          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Title</Text>
            <Text style={styles.detailValue}>{result.title || draft?.title}</Text>
          </View>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Price</Text>
            <Text style={styles.detailValue}>{result.price?.amount} {result.price?.currency}</Text>
          </View>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Status</Text>
            <Text style={styles.detailValue}>{result.status}</Text>
          </View>

          {result.externalUrl && (
            <TouchableOpacity style={styles.primaryBtn} onPress={handleOpenEbay}>
              <Text style={styles.primaryBtnText}>Open on eBay</Text>
            </TouchableOpacity>
          )}

          <TouchableOpacity style={styles.secondaryBtn} onPress={() => router.replace(`/product/tracking?id=${id}`)}>
            <Text style={styles.secondaryBtnText}>Go to Tracking</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    );
  }

  // Publish preview view
  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.header}>Publish to eBay</Text>

      {error && (
        <View style={styles.errorCard}>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      )}

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Preview</Text>

        <View style={styles.detailRow}>
          <Text style={styles.detailLabel}>Title</Text>
          <Text style={styles.detailValue}>{draft?.title || 'No title'}</Text>
        </View>
        <View style={styles.detailRow}>
          <Text style={styles.detailLabel}>Price</Text>
          <Text style={styles.detailValue}>{draft?.price?.amount} {draft?.price?.currency}</Text>
        </View>
        <View style={styles.detailRow}>
          <Text style={styles.detailLabel}>Description</Text>
          <Text style={[styles.detailValue, { flex: 2 }]}>{draft?.description?.slice(0, 200)}...</Text>
        </View>
      </View>

      <View style={styles.warningBox}>
        <Text style={styles.warningText}>
          You are about to publish a real eBay listing. This will create a live offer on your eBay account.
        </Text>
      </View>

      <TouchableOpacity
        style={[styles.primaryBtn, publishing && styles.disabledBtn]}
        onPress={handlePublish}
        disabled={publishing}
      >
        <Text style={styles.primaryBtnText}>{publishing ? 'Publishing...' : 'Publish to eBay'}</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.secondaryBtn} onPress={() => router.back()} disabled={publishing}>
        <Text style={styles.secondaryBtnText}>Cancel</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8f9fa' },
  content: { padding: 16, paddingBottom: 40 },
  center: { justifyContent: 'center', alignItems: 'center' },
  header: { fontSize: 28, fontWeight: 'bold', color: '#111827', marginBottom: 20 },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  successCard: { borderColor: '#22c55e', backgroundColor: '#f0fdf4' },
  cardTitle: { fontSize: 16, fontWeight: '700', color: '#111827', marginBottom: 8 },
  cardDesc: { fontSize: 14, color: '#6b7280', lineHeight: 20, marginBottom: 16 },
  iconBox: {
    width: 56, height: 56, borderRadius: 12, backgroundColor: '#e5e7eb',
    justifyContent: 'center', alignItems: 'center', marginBottom: 12,
  },
  iconText: { fontSize: 11, fontWeight: '700', color: '#6b7280' },
  detailRow: { flexDirection: 'row', paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: '#f3f4f6' },
  detailLabel: { fontSize: 13, fontWeight: '600', color: '#6b7280', width: 100 },
  detailValue: { fontSize: 14, color: '#111827', flex: 1, flexWrap: 'wrap' },
  warningBox: {
    backgroundColor: '#fffbeb',
    borderRadius: 10,
    padding: 14,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#fde68a',
  },
  warningText: { fontSize: 13, color: '#92400e', lineHeight: 18 },
  primaryBtn: {
    backgroundColor: '#111827',
    padding: 14,
    borderRadius: 10,
    alignItems: 'center',
    marginBottom: 10,
  },
  disabledBtn: { backgroundColor: '#9ca3af' },
  primaryBtnText: { color: '#ffffff', fontWeight: '600', fontSize: 15 },
  secondaryBtn: {
    backgroundColor: '#f3f4f6',
    padding: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  secondaryBtnText: { color: '#374151', fontWeight: '600', fontSize: 15 },
  errorCard: {
    backgroundColor: '#fef2f2',
    borderRadius: 10,
    padding: 12,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#fecaca',
  },
  errorText: { color: '#dc2626', fontSize: 13, fontWeight: '500' },
});
