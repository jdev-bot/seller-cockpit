import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, Switch, TextInput,
  Alert, Linking
} from 'react-native';
import { useApi } from '../hooks/useApi';
import { useAuth } from '../hooks/useAuth';
import { SkeletonList } from '../components/Skeleton';
import { ErrorRetry } from '../components/ErrorRetry';
import { OfflineBanner } from '../components/OfflineBanner';
import { LoadingButton } from '../components/LoadingButton';

export default function SettingsScreen() {
  const api = useApi();
  const auth = useAuth();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [connections, setConnections] = useState<any[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [defaultShipping, setDefaultShipping] = useState('');
  const [defaultPackaging, setDefaultPackaging] = useState('');
  const [defaultMode, setDefaultMode] = useState('PRIVATE_DECLUTTERING');

  const [taxMode, setTaxMode] = useState('NONE');
  const [vatRate, setVatRate] = useState('19');
  const [isPro, setIsPro] = useState(false);

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    try {
      setError(null);
      setLoading(true);
      const cons = await api.listConnections();
      setConnections(cons || []);
    } catch (e: any) {
      console.warn('Failed to load settings:', e.message);
      setError(e.message || 'Failed to load settings');
    } finally {
      setLoading(false);
    }
  };

  const handleConnectEbay = async () => {
    try {
      setSaving(true);
      const { url } = await api.getEbayConnectUrl();
      const supported = await Linking.canOpenURL(url);
      if (supported) {
        await Linking.openURL(url);
      } else {
        Alert.alert('Error', 'Cannot open eBay OAuth URL');
      }
    } catch (e: any) {
      Alert.alert('Error', e.message || 'Failed to get eBay connect URL');
    } finally {
      setSaving(false);
    }
  };

  const handleDisconnectEbay = async () => {
    Alert.alert(
      'Disconnect eBay',
      'Remove your eBay account connection?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Disconnect',
          style: 'destructive',
          onPress: async () => {
            try {
              setSaving(true);
              await api.disconnectEbay();
              await loadSettings();
            } catch (e: any) {
              Alert.alert('Error', e.message || 'Failed to disconnect');
            } finally {
              setSaving(false);
            }
          }
        }
      ]
    );
  };

  const handleSaveDefaults = () => {
    setSaving(true);
    setTimeout(() => {
      setSaving(false);
      Alert.alert('Saved', 'Default settings saved.');
    }, 600);
  };

  if (loading) {
    return (
      <View style={styles.container}>
        <OfflineBanner />
        <ScrollView style={styles.container} contentContainerStyle={styles.content}>
          <Text style={styles.header}>Settings</Text>
          <SkeletonList count={4} />
        </ScrollView>
      </View>
    );
  }

  if (error) {
    return (
      <View style={styles.container}>
        <OfflineBanner />
        <ErrorRetry message={error} onRetry={loadSettings} />
      </View>
    );
  }

  const ebayConn = connections.find((c: any) => c.platform === 'EBAY');
  const kzConn = connections.find((c: any) => c.platform === 'KLEINANZEIGEN');

  return (
    <View style={styles.container}>
      <OfflineBanner />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <Text style={styles.header}>Settings</Text>

      {/* Profile */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>Account</Text>
        <Text style={styles.label}>Email</Text>
        <Text style={styles.value}>{auth.user?.email || 'Unknown'}</Text>
        <TouchableOpacity style={styles.dangerBtn} onPress={auth.signOut}>
          <Text style={styles.dangerBtnText}>Sign Out</Text>
        </TouchableOpacity>
      </View>

      {/* Marketplace Connections */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>Marketplace Connections</Text>

        <View style={styles.connRow}>
          <View style={styles.connInfo}>
            <Text style={styles.connName}>eBay</Text>
            <Text style={styles.connStatus}>
              {ebayConn?.connected ? `Connected (expires ${ebayConn.expiresAt?.slice(0,10) || 'soon'})` : 'Not connected'}
            </Text>
          </View>
          <TouchableOpacity
            style={[styles.connBtn, ebayConn?.connected ? styles.connBtnActive : styles.connBtnInactive, saving && { opacity: 0.6 }]}
            onPress={ebayConn?.connected ? handleDisconnectEbay : handleConnectEbay}
            disabled={saving}
          >
            <Text style={[styles.connBtnText, !ebayConn?.connected && { color: '#fff' }]}>
              {saving ? 'Working...' : ebayConn?.connected ? 'Disconnect' : 'Connect'}
            </Text>
          </TouchableOpacity>
        </View>

        <View style={styles.connRow}>
          <View style={styles.connInfo}>
            <Text style={styles.connName}>Kleinanzeigen</Text>
            <Text style={styles.connStatus}>
              {kzConn?.connected ? 'Manual tracking active' : 'Assisted publishing only'}
            </Text>
          </View>
          <View style={[styles.connBtn, { backgroundColor: '#6b7280' }]}>
            <Text style={[styles.connBtnText, { color: '#fff' }]}>Auto</Text>
          </View>
        </View>
      </View>

      {/* Default Costs */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>Default Costs</Text>
        <Text style={styles.hint}>Pre-filled for new product cases</Text>

        <Text style={styles.label}>Default Shipping Cost (EUR)</Text>
        <TextInput
          style={styles.input}
          keyboardType="decimal-pad"
          value={defaultShipping}
          onChangeText={setDefaultShipping}
          placeholder="0.00"
        />

        <Text style={styles.label}>Default Packaging Cost (EUR)</Text>
        <TextInput
          style={styles.input}
          keyboardType="decimal-pad"
          value={defaultPackaging}
          onChangeText={setDefaultPackaging}
          placeholder="0.00"
        />

        <Text style={styles.label}>Default Seller Mode</Text>
        <View style={styles.modeRow}>
          {['PRIVATE_DECLUTTERING', 'PRIVATE_RESELLING', 'PROFESSIONAL'].map((m) => (
            <TouchableOpacity
              key={m}
              style={[styles.modeBtn, defaultMode === m && styles.modeBtnActive]}
              onPress={() => setDefaultMode(m)}
            >
              <Text style={[styles.modeBtnText, defaultMode === m && styles.modeBtnTextActive]}>
                {m.replace(/_/g, ' ')}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        <TouchableOpacity style={styles.saveBtn} onPress={handleSaveDefaults} disabled={saving}>
          <Text style={styles.saveBtnText}>{saving ? 'Saving...' : 'Save Defaults'}</Text>
        </TouchableOpacity>
      </View>

      {/* Professional Tax Profile */}
      <View style={styles.card}>
        <View style={styles.rowBetween}>
          <Text style={styles.cardTitle}>Professional Tax Profile</Text>
          <Switch value={isPro} onValueChange={setIsPro} />
        </View>

        {isPro && (
          <>
            <Text style={styles.label}>VAT Mode</Text>
            <View style={styles.modeRow}>
              {['NONE', 'SMALL_BUSINESS', 'REGULAR_VAT'].map((m) => (
                <TouchableOpacity
                  key={m}
                  style={[styles.modeBtn, taxMode === m && styles.modeBtnActive]}
                  onPress={() => setTaxMode(m)}
                >
                  <Text style={[styles.modeBtnText, taxMode === m && styles.modeBtnTextActive]}>
                    {m.replace(/_/g, ' ')}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <Text style={styles.label}>VAT Rate (%)</Text>
            <TextInput
              style={styles.input}
              keyboardType="decimal-pad"
              value={vatRate}
              onChangeText={setVatRate}
            />

            <Text style={styles.disclaimer}>
              Tax calculations are estimates only and do not constitute legal or financial advice.
            </Text>
          </>
        )}
      </View>

      {/* About */}
      <View style={styles.card}>
        <Text style={styles.cardTitle}>About</Text>
        <Text style={styles.label}>Version</Text>
        <Text style={styles.value}>1.0.0 MVP</Text>
        <TouchableOpacity onPress={() => Linking.openURL('https://github.com/jdev-bot/seller-cockpit')}>
          <Text style={styles.link}>Open Source on GitHub</Text>
        </TouchableOpacity>
      </View>
      </ScrollView>
    </View>
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
  cardTitle: { fontSize: 16, fontWeight: '700', color: '#111827', marginBottom: 12 },
  label: { fontSize: 13, fontWeight: '600', color: '#6b7280', marginBottom: 6, marginTop: 10 },
  value: { fontSize: 15, color: '#111827' },
  hint: { fontSize: 12, color: '#9ca3af', marginBottom: 10 },
  input: {
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 8,
    padding: 10,
    fontSize: 15,
    color: '#111827',
    backgroundColor: '#f9fafb',
  },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  connRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  connInfo: { flex: 1 },
  connName: { fontSize: 15, fontWeight: '600', color: '#111827' },
  connStatus: { fontSize: 12, color: '#6b7280', marginTop: 2 },
  connBtn: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 8 },
  connBtnActive: { backgroundColor: '#fee2e2' },
  connBtnInactive: { backgroundColor: '#111827' },
  connBtnText: { fontSize: 13, fontWeight: '600', color: '#111827' },
  modeRow: { flexDirection: 'row', gap: 8, marginTop: 4 },
  modeBtn: {
    flex: 1,
    paddingVertical: 8,
    paddingHorizontal: 4,
    borderRadius: 8,
    backgroundColor: '#f3f4f6',
    alignItems: 'center',
    marginRight: 8,
  },
  modeBtnActive: { backgroundColor: '#111827' },
  modeBtnText: { fontSize: 12, color: '#6b7280', textTransform: 'capitalize' },
  modeBtnTextActive: { color: '#ffffff', fontWeight: '600' },
  saveBtn: {
    marginTop: 14,
    backgroundColor: '#111827',
    padding: 12,
    borderRadius: 10,
    alignItems: 'center',
  },
  saveBtnText: { color: '#ffffff', fontWeight: '600', fontSize: 15 },
  dangerBtn: {
    marginTop: 14,
    backgroundColor: '#fef2f2',
    padding: 12,
    borderRadius: 10,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#fecaca',
  },
  dangerBtnText: { color: '#dc2626', fontWeight: '600', fontSize: 15 },
  disclaimer: { fontSize: 11, color: '#9ca3af', marginTop: 10, fontStyle: 'italic' },
  link: { fontSize: 14, color: '#2563eb', marginTop: 8 },
});
