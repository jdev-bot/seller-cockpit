import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, ActivityIndicator, TextInput, Alert } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';

export default function TrackingScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [listings, setListings] = useState<any[]>([]);

  const load = useCallback(async () => {
    if (!id) return;
    try {
      const data = await api.getProductCase(id);
      // fetch listings via dashboard endpoint workaround or direct call
      // For simplicity, we re-fetch via dashboard listings
      const listingsData = await fetch(`${process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8080'}/api/dashboard/product-cases/${id}/listings`, { headers: { 'Content-Type': 'application/json' } }).then(r => r.json());
      setListings(Array.isArray(listingsData) ? listingsData : []);
    } catch (e) {
      console.warn(e);
    }
  }, [id, api]);

  useEffect(() => { load(); }, [load]);

  const onMarkSold = async (listingId: string) => {
    await api.updateListingStatus(listingId, 'SOLD');
    await load();
  };

  const onMarkRemoved = async (listingId: string) => {
    await api.updateListingStatus(listingId, 'REMOVED');
    await load();
  };

  const onSaveUrl = async (listingId: string, url: string) => {
    await api.setExternalUrl(listingId, url);
    await load();
  };

  const statusColor = (status: string) => {
    switch (status) {
      case 'ACTIVE': case 'PUBLISHED': return '#22c55e';
      case 'SOLD': return '#3b82f6';
      case 'EXPIRED': case 'REMOVED': return '#9ca3af';
      case 'FAILED': return '#ef4444';
      default: return '#eab308';
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.heading}>Listing Tracking</Text>

      {listings.length === 0 && (
        <View style={styles.empty}>
          <Text style={styles.emptyText}>No listings yet. Generate and publish first.</Text>
          <TouchableOpacity style={styles.btn} onPress={() => router.push(`/product/listings?id=${id}`)}>
            <Text style={styles.btnText}>Go to Listings</Text>
          </TouchableOpacity>
        </View>
      )}

      {listings.map((listing) => (
        <View key={listing.id} style={styles.card}>
          <View style={styles.cardHeader}>
            <Text style={styles.platform}>{listing.platform}</Text>
            <View style={[styles.statusBadge, { backgroundColor: statusColor(listing.status) }]}>
              <Text style={styles.statusText}>{listing.status}</Text>
            </View>
          </View>

          <Text style={styles.title}>{listing.title}</Text>
          <Text style={styles.price}>{listing.price?.amount?.toFixed?.(2) || listing.price} €</Text>

          {listing.externalUrl ? (
            <Text style={styles.url}>URL: {listing.externalUrl}</Text>
          ) : (
            <View style={styles.urlInputRow}>
              <TextInput
                style={styles.urlInput}
                placeholder="Paste listing URL here"
                onSubmitEditing={(e) => onSaveUrl(listing.id, e.nativeEvent.text)}
              />
            </View>
          )}

          <View style={styles.actions}>
            <TouchableOpacity style={[styles.actionBtn, { backgroundColor: '#22c55e' }]} onPress={() => onMarkSold(listing.id)}>
              <Text style={styles.actionText}>Mark Sold</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.actionBtn, { backgroundColor: '#ef4444' }]} onPress={() => onMarkRemoved(listing.id)}>
              <Text style={styles.actionText}>Mark Removed</Text>
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
  card: { backgroundColor: '#ffffff', borderRadius: 12, padding: 14, marginBottom: 12, borderWidth: 1, borderColor: '#f3f4f6' },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  platform: { fontSize: 14, fontWeight: '700', color: '#2563eb', textTransform: 'uppercase' },
  statusBadge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 10 },
  statusText: { color: '#ffffff', fontSize: 10, fontWeight: '700' },
  title: { fontSize: 15, fontWeight: '600', color: '#111827', marginBottom: 2 },
  price: { fontSize: 16, fontWeight: '800', color: '#111827', marginBottom: 8 },
  url: { fontSize: 12, color: '#2563eb', marginBottom: 8 },
  urlInputRow: { marginBottom: 8 },
  urlInput: { borderWidth: 1, borderColor: '#e5e7eb', borderRadius: 8, paddingHorizontal: 10, paddingVertical: 8, fontSize: 14, backgroundColor: '#fafafa' },
  actions: { flexDirection: 'row', gap: 10 },
  actionBtn: { flex: 1, paddingVertical: 10, borderRadius: 8, alignItems: 'center' },
  actionText: { color: '#ffffff', fontWeight: '700', fontSize: 13 },
  btn: { backgroundColor: '#2563eb', paddingVertical: 14, borderRadius: 10, alignItems: 'center' },
  btnText: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
});
