import React, { useState, useEffect, useCallback } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, Alert,
  Image, RefreshControl
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';
import { SkeletonList } from '../components/Skeleton';
import { EmptyState } from '../components/EmptyState';
import { ErrorRetry } from '../components/ErrorRetry';
import { OfflineBanner } from '../components/OfflineBanner';

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
};

export default function ProductCaseDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [pc, setPc] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    try {
      setError(null);
      setLoading(true);
      const data = await api.getProductCase(id);
      setPc(data);
    } catch (e: any) {
      console.warn(e);
      setError(e.message || 'Failed to load product');
    } finally {
      setLoading(false);
      setRefreshing(false);
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

  if (loading) {
    return (
      <View style={styles.container}>
        <OfflineBanner />
        <SkeletonList count={6} />
      </View>
    );
  }

  if (error) {
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
        <EmptyState
          icon="❓"
          title="Product not found"
          subtitle="The product case may have been deleted."
          actionLabel="Back to Dashboard"
          onAction={() => router.replace('/')}
        />
      </View>
    );
  }

  const steps: { label: string; route: string; done: boolean; disabled: boolean }[] = [
    { label: 'Capture Media', route: `/product/capture?id=${id}`, done: pc.status !== 'CAPTURED', disabled: false },
    { label: 'Review Facts', route: `/product/review?id=${id}`, done: !!pc.productFacts?.userConfirmed, disabled: !pc.productFacts },
    { label: 'Market Research', route: `/product/research?id=${id}`, done: !!pc.marketResearchResult, disabled: !pc.productFacts },
    { label: 'Pricing', route: `/product/pricing?id=${id}`, done: !!pc.pricingRecommendation, disabled: !pc.marketResearchResult },
    { label: 'Listings', route: `/product/listings?id=${id}`, done: pc.status === 'PUBLISHED' || pc.status === 'PARTIALLY_PUBLISHED', disabled: !pc.pricingRecommendation },
    { label: 'Tracking', route: `/product/tracking?id=${id}`, done: false, disabled: pc.status !== 'PUBLISHED' && pc.status !== 'PARTIALLY_PUBLISHED' },
  ];

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} />}
    >
      <OfflineBanner />
      {/* Header with image */}
      <View style={styles.headerCard}>
        {pc.mediaAssets?.length > 0 ? (
          <Image
            source={{ uri: pc.mediaAssets[0].thumbnailUrl || pc.mediaAssets[0].storageUrl }}
            style={styles.heroImage}
            resizeMode="cover"
          />
        ) : (
          <View style={styles.heroPlaceholder}>
            <Text style={styles.heroPlaceholderText}>No images yet</Text>
          </View>
        )}
        <View style={styles.headerOverlay}>
          <Text style={styles.title}>{pc.title || pc.productFacts?.title || 'Unnamed Product'}</Text>
          <Text style={styles.mode}>{pc.sellerMode?.replace(/_/g, ' ')}</Text>
          <View style={[styles.statusBadge, { backgroundColor: STATUS_COLORS[pc.status] || '#6b7280' }]}>
            <Text style={styles.statusText}>{STATUS_LABELS[pc.status] || pc.status}</Text>
          </View>
        </View>
      </View>

      {/* Quick Info */}
      {pc.productFacts && (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Detected Facts</Text>
          <FactRow label="Brand" value={pc.productFacts.brand} />
          <FactRow label="Model" value={pc.productFacts.model} />
          <FactRow label="Category" value={pc.productFacts.category} />
          <FactRow label="Variant" value={pc.productFacts.variant} />
          <FactRow label="Condition" value={pc.conditionAssessment?.condition} />
          {pc.conditionAssessment?.visibleDefects?.length > 0 && (
            <FactRow label="Defects" value={pc.conditionAssessment.visibleDefects.join(', ')} />
          )}
        </View>
      )}

      {/* Pricing Summary */}
      {pc.pricingRecommendation && (
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Pricing</Text>
          <PriceRow label="Recommended" value={pc.pricingRecommendation.recommendedPrice} />
          {pc.pricingRecommendation.quickSalePrice && (
            <PriceRow label="Quick Sale" value={pc.pricingRecommendation.quickSalePrice} />
          )}
          {pc.pricingRecommendation.estimatedProfit && (
            <PriceRow label="Est. Profit" value={pc.pricingRecommendation.estimatedProfit} />
          )}
          {pc.pricingRecommendation.roiPercent && (
            <FactRow label="ROI" value={`${pc.pricingRecommendation.roiPercent}%`} />
          )}
        </View>
      )}

      {/* Steps */}
      <Text style={styles.sectionTitle}>Steps</Text>
      {steps.map((step, idx) => (
        <TouchableOpacity
          key={idx}
          style={[styles.stepCard, step.disabled && styles.stepCardDisabled, step.done && styles.stepCardDone]}
          onPress={() => !step.disabled && router.push(step.route)}
          disabled={step.disabled}
        >
          <View style={[styles.stepNum, step.done ? styles.stepNumDone : {}]}>
            <Text style={[styles.stepNumText, step.done ? styles.stepNumTextDone : {}]}>{idx + 1}</Text>
          </View>
          <Text style={styles.stepLabel}>{step.label}</Text>
          <Text style={[styles.stepStatus, step.done ? styles.stepStatusDone : step.disabled ? styles.stepStatusDisabled : {}]}>
            {step.done ? 'Done' : step.disabled ? 'Locked' : 'Open'}
          </Text>
        </TouchableOpacity>
      ))}

      {/* Quick Actions */}
      {pc.status === 'NEEDS_USER_INFO' && (
        <TouchableOpacity
          style={styles.actionBtn}
          onPress={() => router.push(`/product/review?id=${id}`)}
        >
          <Text style={styles.actionBtnText}>Answer Missing Questions</Text>
        </TouchableOpacity>
      )}

      {pc.status === 'READY_FOR_RESEARCH' && (
        <TouchableOpacity
          style={styles.actionBtn}
          onPress={async () => {
            await api.runResearch(id);
            await load();
          }}
        >
          <Text style={styles.actionBtnText}>Run Market Research</Text>
        </TouchableOpacity>
      )}

      {pc.status === 'PRICED' && (
        <TouchableOpacity
          style={styles.actionBtn}
          onPress={async () => {
            await api.generateListings(id);
            await load();
            router.push(`/product/listings?id=${id}`);
          }}
        >
          <Text style={styles.actionBtnText}>Generate Listings</Text>
        </TouchableOpacity>
      )}

      <TouchableOpacity style={styles.deleteBtn} onPress={onDelete}>
        <Text style={styles.deleteText}>Delete Product Case</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

function FactRow({ label, value }: { label: string; value?: string }) {
  if (!value) return null;
  return (
    <View style={styles.factRow}>
      <Text style={styles.factLabel}>{label}</Text>
      <Text style={styles.factValue}>{value}</Text>
    </View>
  );
}

function PriceRow({ label, value }: { label: string; value?: any }) {
  if (!value) return null;
  return (
    <View style={styles.factRow}>
      <Text style={styles.factLabel}>{label}</Text>
      <Text style={styles.priceValue}>
        {value.amount} {value.currency}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8f9fa' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  headerCard: { backgroundColor: '#ffffff', marginBottom: 16 },
  heroImage: { width: '100%', height: 240 },
  heroPlaceholder: { width: '100%', height: 240, backgroundColor: '#e5e7eb', justifyContent: 'center', alignItems: 'center' },
  heroPlaceholderText: { color: '#9ca3af', fontSize: 16 },
  headerOverlay: { padding: 16 },
  title: { fontSize: 22, fontWeight: 'bold', color: '#111827', marginBottom: 4 },
  mode: { fontSize: 14, color: '#6b7280', textTransform: 'capitalize', marginBottom: 8 },
  statusBadge: { alignSelf: 'flex-start', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16 },
  statusText: { color: '#ffffff', fontSize: 12, fontWeight: '700' },
  card: { backgroundColor: '#ffffff', borderRadius: 12, padding: 16, marginHorizontal: 16, marginBottom: 16, borderWidth: 1, borderColor: '#e5e7eb' },
  cardTitle: { fontSize: 15, fontWeight: '700', color: '#111827', marginBottom: 10 },
  sectionTitle: { fontSize: 16, fontWeight: '700', color: '#111827', marginBottom: 10, marginTop: 4, marginHorizontal: 16 },
  factRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: '#f3f4f6' },
  factLabel: { fontSize: 13, color: '#6b7280', flex: 1 },
  factValue: { fontSize: 14, color: '#111827', fontWeight: '500', flex: 1, textAlign: 'right' },
  priceValue: { fontSize: 14, color: '#059669', fontWeight: '700', flex: 1, textAlign: 'right' },
  stepCard: { flexDirection: 'row', alignItems: 'center', padding: 14, backgroundColor: '#ffffff', borderRadius: 10, marginBottom: 8, marginHorizontal: 16, borderWidth: 1, borderColor: '#e5e7eb' },
  stepCardDisabled: { opacity: 0.5, backgroundColor: '#f3f4f6' },
  stepCardDone: { borderColor: '#22c55e', backgroundColor: '#f0fdf4' },
  stepNum: { width: 30, height: 30, borderRadius: 15, backgroundColor: '#2563eb', justifyContent: 'center', alignItems: 'center', marginRight: 12 },
  stepNumDone: { backgroundColor: '#22c55e' },
  stepNumText: { color: '#ffffff', fontWeight: '700', fontSize: 14 },
  stepNumTextDone: { color: '#ffffff' },
  stepLabel: { flex: 1, fontSize: 15, fontWeight: '600', color: '#111827' },
  stepStatus: { fontSize: 12, color: '#6b7280' },
  stepStatusDone: { color: '#22c55e', fontWeight: '600' },
  stepStatusDisabled: { color: '#9ca3af' },
  actionBtn: { marginHorizontal: 16, marginBottom: 12, backgroundColor: '#111827', padding: 14, borderRadius: 10, alignItems: 'center' },
  actionBtnText: { color: '#ffffff', fontWeight: '600', fontSize: 15 },
  deleteBtn: { marginTop: 8, marginBottom: 40, alignSelf: 'center', paddingHorizontal: 20, paddingVertical: 10 },
  deleteText: { color: '#ef4444', fontWeight: '600', fontSize: 14 },
  emptyText: { color: '#6b7280', fontSize: 15 },
});
