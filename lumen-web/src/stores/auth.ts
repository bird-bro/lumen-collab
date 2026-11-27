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
import { firstLeafRouteOf } from '@/routes/menu-paths';
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
  /**
   * Writes the given session fields wholesale and persists them. Station
   * re-derivation is OPT-IN: the session LANDING (login) passes
   * `{ rederiveStation: true }` (the first granted W node, null when none); a
   * `/me` resume (SessionBoot) omits it and KEEPS a still-granted station — the
   * old unconditional re-derive treated every refresh as a landing and flipped
   * the context off the page the user was on (user report 2026-11-24). A
   * no-longer-granted station always falls back to the first granted W.
   */
  setSession: (partial: Partial<AuthSession>, opts?: { rederiveStation?: boolean }) => void;
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
      setSession: (partial, opts) => {
        set(partial);
        // Station context: landing re-derives (login's opt-in); resume keeps a
        // still-granted station; a revoked station falls back to the first W.
        // On station routes the URL owns the display regardless (activeStationForPath).
        const menus = partial.menus ?? get().menus;
        const keep =
          !opts?.rederiveStation &&
          menus.some((m) => m.type === 'W' && m.mid === get().activeWorkstation);
        if (!keep) {
          // Landing rule (user report 2026-11-24): the rederived station must be the
          // station the landing route actually lands in. The landing (and the `/`
          // redirect) walk firstGrantedRoute, which SKIPS pageless stations - so the
          // bare first-W pick desynced the two at the source (放射 childless vs the
          // 超声 landing). Degenerate all-pageless tree keeps the bare first-W.
          const withPages = menus.find((m) => m.type === 'W' && firstLeafRouteOf(m));
          const firstW = withPages ?? menus.find((m) => m.type === 'W');
          set({ activeWorkstation: firstW ? firstW.mid : null });
        }
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
