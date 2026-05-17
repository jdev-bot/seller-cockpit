import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import React from 'react';
import { AuthProvider } from './hooks/useAuth';

export default function RootLayout() {
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
      </Stack>
      <StatusBar style="auto" />
    </AuthProvider>
  );
}
