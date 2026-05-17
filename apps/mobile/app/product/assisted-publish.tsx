import React, { useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, Alert, TextInput,
  Clipboard, Linking
} from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';

export default function AssistedPublishScreen() {
  const { id, draftId } = useLocalSearchParams<{ id: string; draftId: string }>();
  const router = useRouter();
  const api = useApi();
  const [urls, setUrls] = useState<Record<string, string>>({});

  const handleCopyListing = async () => {
    try {
      const drafts = await api.getListingDrafts(id);
      const draft = drafts.find((d: any) => d.id === draftId);
      if (!draft) {
        Alert.alert('Not found', 'Draft not found');
        return;
      }
      const text = `${draft.title}\n\n${draft.description}\n\nPreis: ${draft.price?.amount} €\n\n${draft.conditionText}`;
      Clipboard.setString(text);
      Alert.alert('Copied', 'All listing text copied to clipboard.\nOpen Kleinanzeigen and paste.');
    } catch (e) {
      Alert.alert('Error', 'Failed to copy draft');
    }
  };

  const handleOpenKleinanzeigen = () => {
    Linking.openURL('https://www.kleinanzeigen.de/p-anzeige-aufgeben.html');
  };

  const handleMarkPublished = async (draftId: string) => {
    const url = urls[draftId];
    if (!url || !url.includes('klein')) {
      Alert.alert('URL required', 'Please save the live Kleinanzeigen URL first.');
      return;
    }
    try {
      await api.updateListingStatus(draftId, 'PUBLISHED');
      await api.setExternalUrl(draftId, url);
      Alert.alert('Marked', 'Listing tracked as published.');
      router.replace(`/product/tracking?id=${id}`);
    } catch (e) {
      Alert.alert('Error', 'Failed to update status');
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.heading}>Kleinanzeigen Assisted Publishing</Text>
      <Text style={styles.sub}>
        We cannot publish directly to Kleinanzeigen without an official API.
        Follow these steps to post manually — we will track it for you.
      </Text>

      <View style={styles.stepCard}>
        <Text style={styles.stepNum}>1</Text>
        <Text style={styles.stepTitle}>Copy Listing Text</Text>
        <Text style={styles.stepDesc}>
          All title, description, price, and condition text is copied to your clipboard.
        </Text>
        <TouchableOpacity style={styles.btn} onPress={handleCopyListing}>
          <Text style={styles.btnText}>Copy to Clipboard</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.stepCard}>
        <Text style={styles.stepNum}>2</Text>
        <Text style={styles.stepTitle}>Open Kleinanzeigen</Text>
        <Text style={styles.stepDesc}>Opens the Kleinanzeigen ad creation page in your browser.</Text>
        <TouchableOpacity style={styles.btn} onPress={handleOpenKleinanzeigen}>
          <Text style={styles.btnText}>Open kleinanz...de</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.stepCard}>
        <Text style={styles.stepNum}>3</Text>
        <Text style={styles.stepTitle}>Paste &amp; Publish</Text>
        <Text style={styles.stepDesc}>
          Tap and hold each field on Kleinanzeigen to paste the copied content.
          Upload the product photos manually.
        </Text>
      </View>

      <View style={styles.stepCard}>
        <Text style={styles.stepNum}>4</Text>
        <Text style={styles.stepTitle}>Save Live URL</Text>
        <Text style={styles.stepDesc}>After publishing, paste the live ad URL here.</Text>
        <TextInput
          style={styles.input}
          placeholder="https://www.kleinanzeigen.de/s-anzeige/..."
          value={urls[draftId] || ''}
          onChangeText={(text) => setUrls((prev) => ({ ...prev, [draftId]: text }))}
          autoCapitalize="none"
          autoCorrect={false}
        />
        <TouchableOpacity
          style={[styles.btn, !urls[draftId]?.includes('klein') && styles.btnDisabled]}
          onPress={() => handleMarkPublished(draftId)}
          disabled={!urls[draftId]?.includes('klein')}
        >
          <Text style={styles.btnText}>Mark as Published &amp; Track</Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity style={styles.skip} onPress={() => router.replace(`/product/listings?id=${id}`)}>
        <Text style={styles.skipText}>← Back to Listings</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, backgroundColor: '#f8f9fa', flexGrow: 1 },
  heading: { fontSize: 18, fontWeight: '700', color: '#111827', marginBottom: 6 },
  sub: { fontSize: 13, color: '#6b7280', marginBottom: 14 },
  stepCard: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  stepNum: {
    width: 28, height: 28, borderRadius: 14, backgroundColor: '#111827',
    color: '#ffffff', textAlign: 'center', lineHeight: 28, fontWeight: '700',
    marginBottom: 8,
  },
  stepTitle: { fontSize: 15, fontWeight: '700', color: '#111827', marginBottom: 4 },
  stepDesc: { fontSize: 13, color: '#6b7280', marginBottom: 10, lineHeight: 18 },
  btn: { backgroundColor: '#2563eb', paddingVertical: 12, borderRadius: 10, alignItems: 'center' },
  btnDisabled: { backgroundColor: '#9ca3af' },
  btnText: { color: '#ffffff', fontWeight: '600', fontSize: 14 },
  input: {
    borderWidth: 1, borderColor: '#e5e7eb', borderRadius: 8,
    paddingHorizontal: 10, paddingVertical: 10,
    fontSize: 14, backgroundColor: '#fafafa', marginBottom: 10,
  },
  skip: { marginTop: 8, alignSelf: 'center' },
  skipText: { color: '#6b7280', fontSize: 14 },
});
