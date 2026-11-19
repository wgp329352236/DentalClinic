import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** Absolute path of the app under test. */
const APP_DIR = path.resolve(__dirname, '../../apps/web-antd');

/**
 * Resolve the localStorage key the app persists a Pinia store under.
 *
 * `packages/stores/src/setup.ts` keys every persisted store as
 * `${namespace}-${storeId}`, and `apps/web-antd/src/main.ts` builds the
 * namespace as `${VITE_APP_NAMESPACE}-${VITE_APP_VERSION}-dev`.
 *
 * Both parts are derived here rather than hardcoded: the namespace comes from
 * the app `.env` and the version from the app `package.json` (which is what
 * vite's inject-metadata plugin feeds into VITE_APP_VERSION). Hardcoding a
 * literal like "vben-web-antd-5.7.0-dev" would silently break every E2E test
 * on the next version bump, sending the app to the login page.
 *
 * Note this only works against the dev server: in production builds the stores
 * are persisted through SecureLS (AES) instead of plain localStorage.
 */
export function appStoreKey(storeId: string): string {
  const envFile = fs.readFileSync(path.join(APP_DIR, '.env'), 'utf-8');
  const namespace =
    /^VITE_APP_NAMESPACE=(.*)$/m.exec(envFile)?.[1]?.trim() || 'vben-web-antd';

  const pkg = JSON.parse(
    fs.readFileSync(path.join(APP_DIR, 'package.json'), 'utf-8'),
  ) as { version?: string };
  const version = pkg.version ?? '0.0.0';

  return `${namespace}-${version}-dev-${storeId}`;
}

/** localStorage key of the access store (`useAccessStore`, id `core-access`). */
export const ACCESS_STORE_KEY = appStoreKey('core-access');

/**
 * The access store persists only the fields listed in its `persist.pick`
 * option, so the injected payload has to match that shape — writing a bare
 * `{ accessToken }` under a wrong key leaves the app unauthenticated.
 */
export interface PersistedAccessState {
  accessCodes: string[];
  accessToken: null | string;
  isLockScreen: boolean;
  refreshToken: null | string;
}

/** Build the persisted access-store payload for a logged-in admin session. */
export function buildAccessState(
  accessToken: string,
  refreshToken?: null | string,
): PersistedAccessState {
  return {
    accessCodes: [],
    accessToken,
    isLockScreen: false,
    refreshToken: refreshToken ?? null,
  };
}
