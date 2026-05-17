import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ActivityIndicator } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useAuth } from './hooks/useAuth';

export default function EbayCallbackScreen() {
  const { code, state, error } = useLocalSearchParams<{ code?: string; state?: string; error?: string }>();
  const router = useRouter();
  const auth = useAuth();
  const [status, setStatus] = useState<'processing' | 'success' | 'error'>('processing');
  const [message, setMessage] = useState('Connecting to eBay...');

  useEffect(() => {
    handleCallback();
  }, [code, error]);

  const handleCallback = async () => {
    if (error) {
      setStatus('error');
      setMessage(`Authorization denied: ${error}`);
      return;
    }
    if (!code) {
      setStatus('error');
      setMessage('No authorization code received from eBay.');
      return;
    }

    try {
      const token = await auth.getIdToken();
      const apiBase = process.env.EXPO_PUBLIC_API_URL || 'https://api.sellercockpit.example.com';
      const url = `${apiBase}/api/marketplaces/ebay/callback?code=${encodeURIComponent(code)}&state=${encodeURIComponent(state || '')}`;
      const res = await fetch(url, {
        headers: {
          Accept: 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      });
      if (res.ok) {
        setStatus('success');
        setMessage('eBay account connected successfully!');
        setTimeout(() => {
          router.replace('/settings');
        }, 1500);
      } else {
        const body = await res.text();
        setStatus('error');
        setMessage(`Connection failed: ${body}`);
      }
    } catch (e: any) {
      setStatus('error');
      setMessage(`Network error: ${e.message}`);
    }
  };

  return (
    <View style={styles.container}>
      <ActivityIndicator size="large" color="#2563eb" style={styles.spinner} />
      <Text style={[styles.message, status === 'error' && styles.errorText, status === 'success' && styles.successText]}>
        {message}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8f9fa', justifyContent: 'center', alignItems: 'center', padding: 24 },
  spinner: { marginBottom: 20 },
  message: { fontSize: 16, color: '#374151', textAlign: 'center', lineHeight: 22 },
  errorText: { color: '#dc2626' },
  successText: { color: '#059669' },
});
