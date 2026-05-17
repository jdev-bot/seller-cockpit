import { useEffect, useCallback } from 'react';
import * as Notifications from 'expo-notifications';
import { useApi } from './useApi';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

/**
 * Registers the Expo push token with the backend.
 * Call this on app load (done automatically in index.tsx).
 */
export function usePushNotifications() {
  const api = useApi();

  const register = useCallback(async () => {
    try {
      const { status: existingStatus } = await Notifications.getPermissionsAsync();
      let finalStatus = existingStatus;
      if (existingStatus !== 'granted') {
        const { status } = await Notifications.requestPermissionsAsync();
        finalStatus = status;
      }
      if (finalStatus !== 'granted') {
        console.log('Push notification permission not granted');
        return;
      }

      const tokenData = await Notifications.getExpoPushTokenAsync();
      const token = tokenData.data;
      if (token) {
        await api.registerPushToken(token);
        console.log('Push token registered:', token.slice(0, 20) + '...');
      }

      // Handle foreground notifications
      const sub = Notifications.addNotificationReceivedListener((notification) => {
        console.log('Notification received:', notification.request.content.title);
      });

      return () => {
        Notifications.removeNotificationSubscription(sub);
      };
    } catch (err) {
      console.error('Push notification setup failed:', err);
    }
  }, [api]);

  useEffect(() => {
    register();
  }, [register]);
}
