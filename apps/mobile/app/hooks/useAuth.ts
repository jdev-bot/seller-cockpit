import { useState, useEffect, useCallback, createContext, useContext } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * Minimal auth context using Firebase on the web (works in Expo).
 *
 * NOTE: Setup requires your Firebase config in firebaseConfig.ts.
 * Install: npx expo install firebase @react-native-async-storage/async-storage
 * Add firebaseConfig.ts with your Firebase project config.
 */

const AUTH_STATE_KEY = '@seller_cockpit_auth_state';

export interface AuthState {
  firebaseUid: string;
  email: string | null;
  displayName: string | null;
  idToken: string | null;
  refreshToken: string | null;
  expiresAt: number; // epoch ms
}

interface AuthContextValue {
  state: AuthState | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string, displayName?: string) => Promise<void>;
  signInWithGoogle: () => Promise<void>;
  signOut: () => Promise<void>;
  getIdToken: () => Promise<string | null>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Load persisted auth on mount
  useEffect(() => {
    AsyncStorage.getItem(AUTH_STATE_KEY).then(raw => {
      if (raw) {
        const parsed = JSON.parse(raw) as AuthState;
        if (parsed.expiresAt > Date.now()) {
          setState(parsed);
        } else {
          AsyncStorage.removeItem(AUTH_STATE_KEY);
        }
      }
      setIsLoading(false);
    });
  }, []);

  const saveState = useCallback((newState: AuthState | null) => {
    setState(newState);
    if (newState) {
      AsyncStorage.setItem(AUTH_STATE_KEY, JSON.stringify(newState));
    } else {
      AsyncStorage.removeItem(AUTH_STATE_KEY);
    }
  }, []);

  /**
   * Sign up with email/password using Firebase REST auth API.
   * This avoids heavy Firebase JS SDK and works in any RN environment.
   */
  const signUp = useCallback(async (email: string, password: string, displayName?: string) => {
    const FIREBASE_API_KEY = process.env.EXPO_PUBLIC_FIREBASE_API_KEY;
    if (!FIREBASE_API_KEY) throw new Error('Missing Firebase API key');

    const res = await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${FIREBASE_API_KEY}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, returnSecureToken: true }),
      }
    );
    const data = await res.json();
    if (!res.ok) throw new Error(data.error?.message || 'Sign up failed');

    // Optionally update profile
    if (displayName) {
      await fetch(
        `https://identitytoolkit.googleapis.com/v1/accounts:update?key=${FIREBASE_API_KEY}`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ idToken: data.idToken, displayName, returnSecureToken: false }),
        }
      );
    }

    saveState({
      firebaseUid: data.localId,
      email: data.email,
      displayName: displayName || data.displayName || null,
      idToken: data.idToken,
      refreshToken: data.refreshToken,
      expiresAt: Date.now() + Number(data.expiresIn) * 1000,
    });
  }, [saveState]);

  /**
   * Sign in with email/password using Firebase REST auth API.
   */
  const signIn = useCallback(async (email: string, password: string) => {
    const FIREBASE_API_KEY = process.env.EXPO_PUBLIC_FIREBASE_API_KEY;
    if (!FIREBASE_API_KEY) throw new Error('Missing Firebase API key');

    const res = await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_API_KEY}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, returnSecureToken: true }),
      }
    );
    const data = await res.json();
    if (!res.ok) throw new Error(data.error?.message || 'Sign in failed');

    saveState({
      firebaseUid: data.localId,
      email: data.email,
      displayName: data.displayName || null,
      idToken: data.idToken,
      refreshToken: data.refreshToken,
      expiresAt: Date.now() + Number(data.expiresIn) * 1000,
    });
  }, [saveState]);

  /**
   * Google OAuth sign-in via expo-auth-session.
   */
  const signInWithGoogle = useCallback(async () => {
    // This is a placeholder for Google OAuth integration.
    // Full implementation requires expo-auth-session + Google client setup.
    throw new Error('Google Sign-In not implemented yet. Use email/password for now.');
  }, [saveState]);

  const signOutUser = useCallback(async () => {
    saveState(null);
  }, [saveState]);

  /**
   * Returns a valid ID token, refreshing if within 5 minutes of expiry.
   */
  const getIdToken = useCallback(async (): Promise<string | null> => {
    if (!state) return null;
    if (state.expiresAt > Date.now() + 5 * 60 * 1000) {
      return state.idToken;
    }
    // Refresh
    const FIREBASE_API_KEY = process.env.EXPO_PUBLIC_FIREBASE_API_KEY;
    if (!FIREBASE_API_KEY) return state.idToken;
    try {
      const res = await fetch(
        `https://securetoken.googleapis.com/v1/token?key=${FIREBASE_API_KEY}`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: `grant_type=refresh_token&refresh_token=${encodeURIComponent(state.refreshToken || '')}`,
        }
      );
      const data = await res.json();
      if (!res.ok) return state.idToken;
      const newState: AuthState = {
        ...state,
        idToken: data.id_token,
        refreshToken: data.refresh_token || state.refreshToken,
        expiresAt: Date.now() + Number(data.expires_in) * 1000,
      };
      saveState(newState);
      return data.id_token;
    } catch {
      return state.idToken;
    }
  }, [state, saveState]);

  return (
    <AuthContext.Provider
      value={{
        state,
        isLoading,
        isAuthenticated: !!state,
        signIn,
        signUp,
        signInWithGoogle,
        signOut: signOutUser,
        getIdToken,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
