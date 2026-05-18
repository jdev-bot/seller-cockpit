import React from 'react';
import { TouchableOpacity, Text, StyleSheet, ActivityIndicator, ViewStyle } from 'react-native';

interface LoadingButtonProps {
  label: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  variant?: 'primary' | 'secondary' | 'danger';
  style?: ViewStyle;
}

export function LoadingButton({ label, onPress, loading = false, disabled = false, variant = 'primary', style }: LoadingButtonProps) {
  const isDisabled = disabled || loading;
  const colors = {
    primary: { bg: '#2563eb', text: '#fff' },
    secondary: { bg: '#e2e8f0', text: '#334155' },
    danger: { bg: '#dc2626', text: '#fff' },
  };
  const theme = colors[variant];

  return (
    <TouchableOpacity
      style={[{ backgroundColor: theme.bg, opacity: isDisabled ? 0.6 : 1 }, styles.button, style]}
      onPress={onPress}
      disabled={isDisabled}
      activeOpacity={0.8}
    >
      {loading ? (
        <ActivityIndicator size="small" color={theme.text} />
      ) : (
        <Text style={[styles.text, { color: theme.text }]}>{label}</Text>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  button: {
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 120,
  },
  text: {
    fontSize: 15,
    fontWeight: '600',
  },
});
