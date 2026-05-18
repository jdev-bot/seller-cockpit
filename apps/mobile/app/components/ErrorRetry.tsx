import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';

interface ErrorRetryProps {
  message: string;
  onRetry: () => void;
}

export function ErrorRetry({ message, onRetry }: ErrorRetryProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.icon}>⚠️</Text>
      <Text style={styles.title}>Something went wrong</Text>
      <Text style={styles.message}>{message}</Text>
      <TouchableOpacity style={styles.button} onPress={onRetry}>
        <Text style={styles.buttonText}>Retry</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
    marginTop: 48,
  },
  icon: { fontSize: 40, marginBottom: 12 },
  title: { fontSize: 18, fontWeight: '600', color: '#1e293b', marginBottom: 6 },
  message: { fontSize: 14, color: '#64748b', textAlign: 'center', marginBottom: 16 },
  button: { backgroundColor: '#dc2626', paddingVertical: 10, paddingHorizontal: 20, borderRadius: 8 },
  buttonText: { color: '#fff', fontSize: 14, fontWeight: '600' },
});
