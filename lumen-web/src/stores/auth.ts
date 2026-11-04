/**
 * Auth session store (batch 4 of web-foundation-auth-console, task 4.2;
 * design D5): zustand with the persist middleware on the localStorage key
 * 'lumen-auth', hydrated at store creation. State is exactly the session
 * quadruple - token, profile, menu tree, perms set - so the router guard, the
 * shell navigation, and the pages all read one store.
 *
 * This module also closes the 4.1 injection loop: it configures the transport
 * client with this store's token getter and a session-expiry callback that
 * clears the store. The client never imports this module, so the dependency
 * edge stays one-way (store -> client).
 */
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import { configureApiClient } from '@/api/client';
import type { MenuNode, UserProfile } from '@/api/types';

/** The persisted session quadruple. */
export interface AuthSession {
  token: string | null;
  profile: UserProfile | null;
  menus: MenuNode[];
  perms: string[];
}

interface AuthState extends AuthSession {
  /** Writes the given session fields wholesale and persists them (partial writes keep untouched fields). */
  setSession: (partial: Partial<AuthSession>) => void;
  /** Empties all four session fields and persists the cleared session. */
  clear: () => void;
}

const EMPTY_SESSION: AuthSession = { token: null, profile: null, menus: [], perms: [] };

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      ...EMPTY_SESSION,
      setSession: (partial) => set(partial),
      clear: () => set({ ...EMPTY_SESSION }),
    }),
    {
      name: 'lumen-auth',
      storage: createJSONStorage(() => localStorage),
    },
  ),
);

configureApiClient({
  getToken: () => useAuthStore.getState().token,
  onSessionExpired: () => useAuthStore.getState().clear(),
});
