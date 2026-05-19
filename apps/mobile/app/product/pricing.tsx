import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, ActivityIndicator } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';
import { SkeletonList } from '../components/Skeleton';
import { ErrorRetry } from '../components/ErrorRetry';
import { OfflineBanner } from '../components/OfflineBanner';

export default function PricingScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [pc, setPc] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [calculating, setCalculating] = useState(false);
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
      setError(e.message || 'Failed to load pricing');
    } finally {
      setLoading(false);
    }
  }, [id, api]);

  useEffect(() => { load(); }, [load]);

  const onRecalculate = async () => {
    if (!id) return;
    setCalculating(true);
    try {
      await api.recalculatePricing(id, pc?.pricingProfile);
      await load();
    } catch (e: any) {
      console.warn(e);
      setError(e.message || 'Pricing calculation failed');
    } finally {
      setCalculating(false);
    }
  };

  if (loading) {
    return (
      <View style={styles.container}>
        <OfflineBanner />
        <SkeletonList count={5} />
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
        <View style={styles.center}><ActivityIndicator size="large" color="#2563eb" /></View>
      </View>
    );
  }

  const rec = pc.pricingRecommendation;
  const mode = pc.sellerMode;

  const renderPrivateDecluttering = () => (
    <>
      <PriceRow label="Quick-sale price" value={rec?.quickSalePrice?.amount} />
      <PriceRow label="Recommended price" value={rec?.recommendedPrice?.amount} bold />
      <PriceRow label="Optimistic price" value={rec?.optimisticPrice?.amount} />
      <PriceRow label="Expected payout" value={rec?.expectedPayout?.amount} highlight />
    </>
  );

  const renderPrivateReselling = () => (
    <>
      <PriceRow label="Purchase price" value={pc.pricingProfile?.purchasePrice?.amount} />
      <PriceRow label="Break-even price" value={rec?.breakEvenPrice?.amount} />
      <PriceRow label="Do not sell below" value={rec?.doNotSellBelowPrice?.amount} warning />
      <PriceRow label="Recommended price" value={rec?.recommendedPrice?.amount} bold />
      <PriceRow label="Estimated profit" value={rec?.estimatedProfit?.amount} highlight />
      <PriceRow label="ROI" value={rec?.roiPercent ? `${rec.roiPercent}%` : undefined} />
    </>
  );

  const renderProfessional = () => (
    <>
      <PriceRow label="Purchase price (net)" value={pc.pricingProfile?.purchasePrice?.amount} />
      <PriceRow label="Gross selling price" value={rec?.recommendedPrice?.amount} bold />
      <PriceRow label="Net revenue" value={rec?.taxBreakdown?.netRevenue?.amount} />
      <PriceRow label="VAT estimate" value={rec?.taxBreakdown?.vatAmount?.amount} />
      <PriceRow label="Platform fees" value={rec?.feeBreakdown?.platformFee?.amount} />
      <PriceRow label="Total costs" value={rec?.feeBreakdown?.totalCosts?.amount} />
      <PriceRow label="Net profit" value={rec?.netProfit?.amount} highlight />
      <PriceRow label="Margin" value={rec?.marginPercent ? `${rec.marginPercent}%` : undefined} />
    </>
  );

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <OfflineBanner />
      <Text style={styles.heading}>Pricing</Text>
      <Text style={styles.mode}>Mode: {mode.replace(/_/g, ' ')}</Text>

      {!rec && (
        <View style={styles.empty}>
          <Text style={styles.emptyText}>No pricing calculated yet.</Text>
          <TouchableOpacity style={styles.btn} onPress={onRecalculate} disabled={calculating}>
            <Text style={styles.btnText}>{calculating ? 'Calculating...' : 'Calculate Prices'}</Text>
          </TouchableOpacity>
        </View>
      )}

      {rec && (
        <>
          <View style={styles.pricingCard}>
            {mode === 'PRIVATE_DECLUTTERING' && renderPrivateDecluttering()}
            {mode === 'PRIVATE_RESELLING' && renderPrivateReselling()}
            {mode === 'PROFESSIONAL' && renderProfessional()}
          </View>

          <View style={styles.feesCard}>
            <Text style={styles.feesTitle}>Fee Breakdown</Text>
            <PriceRow label="Platform fee" value={rec?.feeBreakdown?.platformFee?.amount} />
            <PriceRow label="Shipping" value={rec?.feeBreakdown?.shippingCost?.amount} />
            <PriceRow label="Packaging" value={rec?.feeBreakdown?.packagingCost?.amount} />
            <PriceRow label="Other" value={rec?.feeBreakdown?.otherCosts?.amount} />
          </View>

          <View style={styles.explainBox}>
            <Text style={styles.explainText}>{rec.explanation}</Text>
            <Text style={styles.confidenceText}>Confidence: {rec.confidence}</Text>
          </View>

          {error && (
            <View style={styles.errorCard}>
              <Text style={styles.errorText}>{error}</Text>
            </View>
          )}

          <TouchableOpacity style={styles.btn} onPress={onRecalculate} disabled={calculating}>
            <Text style={styles.btnText}>{calculating ? 'Recalculating...' : 'Recalculate'}</Text>
          </TouchableOpacity>

          <TouchableOpacity style={[styles.btn, { backgroundColor: '#16a34a' }]} onPress={() => router.push(`/product/listings?id=${id}`)}>
            <Text style={styles.btnText}>Go to Listings</Text>
          </TouchableOpacity>
        </>
      )}
    </ScrollView>
  );
}

function PriceRow({ label, value, bold, highlight, warning }: { label: string; value?: string | number; bold?: boolean; highlight?: boolean; warning?: boolean }) {
  if (value === undefined || value === null) return null;
  const isNum = typeof value === 'number';
  const text = isNum ? `${value.toFixed(2)} €` : `${value}`;
  const color = warning ? '#ef4444' : highlight ? '#16a34a' : bold ? '#111827' : '#374151';
  const weight = bold || highlight ? '700' : '600';
  return (
    <View style={styles.priceRow}>
      <Text style={styles.priceLabel}>{label}</Text>
      <Text style={[styles.priceValue, { color, fontWeight: weight }]}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8f9fa' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  heading: { fontSize: 18, fontWeight: '700', color: '#111827', marginBottom: 4, marginTop: 12, marginHorizontal: 16 },
  mode: { fontSize: 13, color: '#6b7280', marginBottom: 12, textTransform: 'capitalize', marginHorizontal: 16 },
  empty: { alignItems: 'center', marginTop: 40 },
  emptyText: { color: '#6b7280', fontSize: 14, marginBottom: 16 },
  pricingCard: { backgroundColor: '#ffffff', borderRadius: 12, padding: 14, marginBottom: 12, marginHorizontal: 16, borderWidth: 1, borderColor: '#f3f4f6' },
  priceRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 7, borderBottomWidth: 1, borderBottomColor: '#f3f4f6' },
  priceLabel: { fontSize: 14, color: '#6b7280' },
  priceValue: { fontSize: 15, color: '#111827' },
  feesCard: { backgroundColor: '#ffffff', borderRadius: 12, padding: 14, marginBottom: 12, marginHorizontal: 16, borderWidth: 1, borderColor: '#f3f4f6' },
  feesTitle: { fontSize: 14, fontWeight: '700', color: '#374151', marginBottom: 6 },
  explainBox: { backgroundColor: '#f0f9ff', borderRadius: 10, padding: 12, marginBottom: 12, marginHorizontal: 16 },
  explainText: { fontSize: 13, color: '#0c4a6e', lineHeight: 18 },
  confidenceText: { fontSize: 12, color: '#0ea5e9', marginTop: 6, fontWeight: '600' },
  errorCard: { backgroundColor: '#fef2f2', borderRadius: 10, padding: 12, marginBottom: 12, marginHorizontal: 16, borderWidth: 1, borderColor: '#fecaca' },
  errorText: { color: '#dc2626', fontSize: 13, fontWeight: '500' },
  btn: { backgroundColor: '#2563eb', paddingVertical: 14, borderRadius: 10, alignItems: 'center', marginBottom: 10, marginHorizontal: 16 },
  btnText: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
});
