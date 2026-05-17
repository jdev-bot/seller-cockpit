import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import React, { useEffect } from 'react';
import { Linking } from 'react-native';
import { AuthProvider } from './hooks/useAuth';

export default function RootLayout() {
  useEffect(() => {
    // Handle deep links (e.g., seller-cockpit://ebay/callback?code=...)
    const sub = Linking.addEventListener('url', (event) => {
      const url = event.url;
      if (url.includes('/ebay/callback')) {
        // Route to ebay-callback screen which parses query params
        // Expo Router handles this automatically if the URL scheme is registered
      }
    });
    return () => sub.remove();
  }, []);

  return (
    <AuthProvider>
      <Stack
        screenOptions={{
          headerShown: false,
          contentStyle: { backgroundColor: '#f8f9fa' },
        }}
      >
        <Stack.Screen name="(login)" options={{ headerShown: false }} />
        <Stack.Screen
          name="index"
          options={{
            title: 'Seller Cockpit',
            headerShown: true,
            headerStyle: { backgroundColor: '#111827' },
            headerTintColor: '#fff',
            headerTitleStyle: { fontWeight: 'bold' },
          }}
        />
        <Stack.Screen
          name="product/[id]"
          options={{
            title: 'New Product',
            presentation: 'modal',
          }}
        />
        <Stack.Screen
          name="product/review"
          options={{
            title: 'Review Product',
            presentation: 'modal',
          }}
        />
        <Stack.Screen
          name="product/research"
          options={{
            title: 'Market Research',
            presentation: 'modal',
          }}
        />
        <Stack.Screen
          name="product/pricing"
          options={{
            title: 'Pricing',
            presentation: 'modal',
          }}
        />
        <Stack.Screen
          name="product/listings"
          options={{
            title: 'Listings',
            presentation: 'modal',
          }}
        />
        <Stack.Screen
          name="product/capture"
          options={{
            title: 'Capture Product',
            presentation: 'modal',
          }}
        />
        <Stack.Screen
          name="product/tracking"
          options={{
            title: 'Tracking',
            presentation: 'modal',
          }}
        />
        <Stack.Screen
          name="product/assisted-publish"
          options={{
            title: 'Publish to Kleinanzeigen',
            presentation: 'modal',
          }}
        />
        <Stack.Screen
          name="product/ebay-publish"
          options={{
            title: 'Publish to eBay',
            presentation: 'modal',
          }}
        />
        <Stack.Screen
          name="ebay-callback"
          options={{
            title: 'eBay Connection',
            presentation: 'modal',
          }}
        />
        <Stack.Screen
          name="settings/index"
          options={{
            title: 'Settings',
            presentation: 'modal',
          }}
        />
      </Stack>
      <StatusBar style="auto" />
    </AuthProvider>
  );
}
