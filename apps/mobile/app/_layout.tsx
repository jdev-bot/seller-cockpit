import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import React from 'react';
import { ApiProvider } from './hooks/useApi';

export default function RootLayout() {
  return (
    <ApiProvider>
      <Stack screenOptions={{ headerShown: true }}>
        <Stack.Screen name="index" options={{ title: 'Seller Cockpit' }} />
        <Stack.Screen name="product/new" options={{ title: 'New Product' }} />
        <Stack.Screen name="product/[id]" options={{ title: 'Product Case' }} />
        <Stack.Screen name="product/capture" options={{ title: 'Capture Media' }} />
        <Stack.Screen name="product/review" options={{ title: 'Review Facts' }} />
        <Stack.Screen name="product/research" options={{ title: 'Market Research' }} />
        <Stack.Screen name="product/pricing" options={{ title: 'Pricing' }} />
        <Stack.Screen name="product/listings" options={{ title: 'Listings' }} />
        <Stack.Screen name="product/tracking" options={{ title: 'Tracking' }} />
      </Stack>
      <StatusBar style="auto" />
    </ApiProvider>
  );
}
