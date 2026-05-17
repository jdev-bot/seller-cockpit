import React, { useEffect } from 'react';
import { View, Text, FlatList, TouchableOpacity, StyleSheet, RefreshControl, ActivityIndicator } from 'react-native';
import { useRouter } from 'expo-router';
import { useApi } from './hooks/useApi';
import { useAuth } from './hooks/useAuth';
import { useState } from 'react';
import { usePushNotifications } from './hooks/usePushNotifications';

const STATUS_LABELS: Record<string, string> = {
  CAPTURED: 'Captured',
  PROCESSING_MEDIA: 'Processing',
  NEEDS_USER_INFO: 'Needs Info',
  READY_FOR_RESEARCH: 'Ready',
  RESEARCHING: 'Researching',
  PRICED: 'Priced',
  LISTING_READY: 'Listing Ready',
  PARTIALLY_PUBLISHED: 'Partially Published',
  PUBLISHED: 'Published',
  SOLD: 'Sold',
  ARCHIVED: 'Archived',
  FAILED: 'Failed',
};

const STATUS_COLORS: Record<string, string> = {
  CAPTURED: '#6b7280',
  PROCESSING_MEDIA: '#2563eb',
  NEEDS_USER_INFO: '#ea580c',
  READY_FOR_RESEARCH: '#6b7280',
  RESEARCHING: '#2563eb',
  PRICED: '#059669',
  LISTING_READY: '#059669',
  PARTIALLY_PUBLISHED: '#7c3aed',
  PUBLISHED: '#7c3aed',
  SOLD: '#059669',
  ARCHIVED: '#6b7280',
  FAILED: '#dc2626',
};

export default function DashboardScreen() {
  const router = useRouter();
  const api = useApi();
  const auth = useAuth();
  const [cases, setCases] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // Register push notifications
  usePushNotifications();

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated) {
      router.replace('/(login)');
    }
  }, [auth.isLoading, auth.isAuthenticated]);

  const load = async () => {
    try {
      setLoading(true);
      const items = await api.listProductCases();
      setCases(items);
    } catch (err: any) {
      console.error('Failed to load cases:', err.message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    if (auth.isAuthenticated) load();
  }, [auth.isAuthenticated]);

  const onRefresh = () => {
    setRefreshing(true);
    load();
  };

  const handleNewProduct = () => {
    router.push('/product/new');
  };

  if (auth.isLoading) {
    return (
      <View style={[styles.container, styles.center]}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  if (!auth.isAuthenticated) {
    return null; // Will redirect
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Seller Cockpit</Text>
        <TouchableOpacity onPress={() => auth.signOut()} style={styles.logoutBtn}>
          <Text style={styles.logoutText}>Logout</Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity style={styles.newButton} onPress={handleNewProduct}>
        <Text style={styles.newButtonText}>+ New Product</Text>
      </TouchableOpacity>

      {loading ? (
        <ActivityIndicator style={{ marginTop: 40 }} />
      ) : cases.length === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>No products yet</Text>
          <Text style={styles.emptyText}>
            Tap "New Product" to list your first item.
          </Text>
        </View>
      ) : (
        <FlatList
          data={cases}
          keyExtractor={(item) => item.id}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
          renderItem={({ item }) => (
            <TouchableOpacity
              style={styles.card}
              onPress={() => router.push(`/product/${item.id}`)}
            >
              <View style={styles.cardRow}>
                <Text style={styles.cardTitle}>{item.title || 'Untitled'}</Text>
                <View
                  style={[
                    styles.badge,
                    { backgroundColor: STATUS_COLORS[item.status] || '#6b7280' },
                  ]}
                >
                  <Text style={styles.badgeText}>
                    {STATUS_LABELS[item.status] || item.status}
                  </Text>
                </View>
              </View>

              <View style={styles.cardMeta}>
                <Text style={styles.metaText}>{item.sellerMode?.replace('_', ' ')}</Text>
                {item.pricingRecommendation?.recommendedPrice && (
                  <Text style={styles.metaText}>
                    {item.pricingRecommendation.recommendedPrice.amount} {' '}
                    {item.pricingRecommendation.recommendedPrice.currency}
                  </Text>
                )}
              </View>
            </TouchableOpacity>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8f9fa' },
  center: { justifyContent: 'center', alignItems: 'center' },
  header: {
    padding: 20,
    paddingTop: 48,
    backgroundColor: '#111827',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  headerTitle: { fontSize: 20, fontWeight: 'bold', color: '#fff' },
  logoutBtn: { padding: 8 },
  logoutText: { color: '#9ca3af' },
  newButton: {
    margin: 16,
    backgroundColor: '#111827',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  newButtonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginHorizontal: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  cardRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  cardTitle: { fontSize: 16, fontWeight: '600', color: '#111827', flex: 1 },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 999,
    marginLeft: 8,
  },
  badgeText: { color: '#fff', fontSize: 12, fontWeight: '500' },
  cardMeta: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  metaText: { color: '#6b7280', fontSize: 13 },
  empty: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 40 },
  emptyTitle: { fontSize: 18, fontWeight: '600', color: '#111827', marginBottom: 8 },
  emptyText: { color: '#6b7280', textAlign: 'center' },
});
