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


/**
 * The persisted session quintuple (workstation-context-web, W-node model): menus
 * carry the type-W station nodes; `activeWorkstation` is the ACTIVE W node's MID
 * (null = console view). Session-scoped: re-login re-derives it (the FIRST granted
 * W node per the user's display rule).
 */
export interface AuthSession {
  token: string | null;
  profile: UserProfile | null;
  menus: MenuNode[];
  perms: string[];
  activeWorkstation: number | null;
}

interface AuthState extends AuthSession {
  /** Writes the given session fields wholesale and persists them; a session LANDING re-derives activeWorkstation (the FIRST granted W node, null when none). */
  setSession: (partial: Partial<AuthSession>) => void;
  /** Session-scoped station switch (re-login re-lands on the first W). */
  setActiveWorkstation: (wsMid: number | null) => void;
  /** Empties all session fields and persists the cleared session. */
  clear: () => void;
}

const EMPTY_SESSION: AuthSession = {
  token: null, profile: null, menus: [], perms: [], activeWorkstation: null,
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      ...EMPTY_SESSION,
      setSession: (partial) => {
        set(partial);
        // a session LANDING re-derives the active station: the FIRST granted W node
        // (user display rule: 默认展示第一个); switching is session-scoped via
        // setActiveWorkstation
        const firstW = (partial.menus ?? get().menus).find((m) => m.type === 'W');
        set({ activeWorkstation: firstW ? firstW.mid : null });
      },
      // a switch to a mid with no granted W node is ignored (nothing to activate)
      setActiveWorkstation: (wsMid) => {
        const exists = get().menus.some((m) => m.mid === wsMid && m.type === 'W');
        if (exists) set({ activeWorkstation: wsMid });
      },
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
