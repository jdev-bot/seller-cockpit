import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import React from 'react';
import { ApiProvider } from './hooks/useApi';
import { usePushNotifications } from './hooks/usePushNotifications';

export default function RootLayout() {
  NotificationsSetup();
  return (
    <ApiProvider>
      <Stack>
        <Stack.Screen name="index" options={{ title: 'Seller Cockpit' }} />
        <Stack.Screen name="product/new" options={{ title: 'New Product', presentation: 'modal' }} />
        <Stack.Screen name="product/capture" options={{ title: 'Capture' }} />
        <Stack.Screen name="product/review" options={{ title: 'Review' }} />
        <Stack.Screen name="product/research" options={{ title: 'Research' }} />
        <Stack.Screen name="product/pricing" options={{ title: 'Pricing' }} />
        <Stack.Screen name="product/listings" options={{ title: 'Listings' }} />
        <Stack.Screen name="product/tracking" options={{ title: 'Tracking' }} />
      </Stack>
      <StatusBar style="auto" />
    </ApiProvider>
  );
}

function NotificationsSetup() {
  usePushNotifications();
  return null;
}
