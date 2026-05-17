import React, { useEffect, useState, useCallback } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, RefreshControl } from 'react-native';
import { useRouter } from 'expo-router';
import { useApi, DashboardItem } from './hooks/useApi';

export default function DashboardScreen() {
  const router = useRouter();
  const api = useApi();
  const [items, setItems] = useState<DashboardItem[]>([]);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await api.getDashboard();
      setItems(data);
    } catch (e) {
      console.warn(e);
    }
  }, [api]);

  useEffect(() => {
    load();
  }, [load]);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }, [load]);

  const statusBadgeColor = (status?: string) => {
    switch (status) {
      case 'PUBLISHED': case 'ACTIVE': return '#22c55e';
      case 'SOLD': return '#3b82f6';
      case 'DRAFT': return '#eab308';
      case 'FAILED': return '#ef4444';
      default: return '#6b7280';
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Seller Cockpit</Text>
        <TouchableOpacity style={styles.newBtn} onPress={() => router.push('/product/new')}>
          <Text style={styles.newBtnText}>+ New Product</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        data={items}
        keyExtractor={(item) => item.id}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => (
          <TouchableOpacity style={styles.card} onPress={() => router.push(`/product/${item.id}`)}>
            <View style={styles.cardHeader}>
              <Text style={styles.cardTitle}>{item.title || 'Unnamed Product'}</Text>
              <View style={[styles.badge, { backgroundColor: statusBadgeColor(item.status) }]}>
                <Text style={styles.badgeText}>{item.status.replace(/_/g, ' ')}</Text>
              </View>
            </View>
            <View style={styles.cardRow}>
              <Text style={styles.cardMode}>{item.mode.replace(/_/g, ' ')}</Text>
            </View>
            <View style={styles.platformRow}>
              <View style={[styles.platformBadge, { backgroundColor: statusBadgeColor(item.ebayStatus) }]}>
                <Text style={styles.platformText}>eBay: {item.ebayStatus || '—'}</Text>
              </View>
              <View style={[styles.platformBadge, { backgroundColor: statusBadgeColor(item.kleinanzeigenStatus) }]}>
                <Text style={styles.platformText}>Kleinanzeigen: {item.kleinanzeigenStatus || '—'}</Text>
              </View>
            </View>
            <View style={styles.actionRow}>
              <Text style={styles.actionText}>Next: {item.nextAction}</Text>
            </View>
          </TouchableOpacity>
        )}
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyText}>No products yet. Tap + to start.</Text>
          </View>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8f9fa' },
  header: { padding: 16, backgroundColor: '#ffffff', borderBottomWidth: 1, borderBottomColor: '#e5e7eb', flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  headerTitle: { fontSize: 20, fontWeight: '700', color: '#111827' },
  newBtn: { backgroundColor: '#2563eb', paddingHorizontal: 14, paddingVertical: 8, borderRadius: 8 },
  newBtnText: { color: '#ffffff', fontWeight: '600', fontSize: 14 },
  list: { padding: 12, gap: 10 },
  card: { backgroundColor: '#ffffff', borderRadius: 12, padding: 14, shadowColor: '#000', shadowOpacity: 0.04, shadowRadius: 6, elevation: 2, borderWidth: 1, borderColor: '#f3f4f6' },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 },
  cardTitle: { fontSize: 16, fontWeight: '700', color: '#111827', flex: 1, marginRight: 8 },
  badge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 12 },
  badgeText: { color: '#ffffff', fontSize: 10, fontWeight: '700', textTransform: 'capitalize' },
  cardRow: { marginBottom: 4 },
  cardMode: { fontSize: 12, color: '#6b7280', textTransform: 'capitalize' },
  platformRow: { flexDirection: 'row', gap: 8, marginTop: 6 },
  platformBadge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
  platformText: { color: '#ffffff', fontSize: 10, fontWeight: '600' },
  actionRow: { marginTop: 8, paddingTop: 8, borderTopWidth: 1, borderTopColor: '#f3f4f6' },
  actionText: { fontSize: 13, color: '#2563eb', fontWeight: '600' },
  empty: { padding: 40, alignItems: 'center' },
  emptyText: { color: '#9ca3af', fontSize: 14 },
});
