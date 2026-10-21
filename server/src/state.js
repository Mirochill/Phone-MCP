import { existsSync, readFileSync, writeFileSync, renameSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

// Only hashed tokens and OAuth grants persist; never screen or notification content.
export function durableOAuthState(file, issuer) {
  const names = ['clients', 'access', 'refresh'];
  let saved;
  if (file && existsSync(file)) {
    saved = JSON.parse(readFileSync(file, 'utf8'));
    if (saved.issuer !== issuer || saved.version !== 1) saved = undefined;
  }
  const maps = Object.fromEntries(names.map(name => [name, new Map(saved?.[name] ?? [])]));
  const persist = () => {
    if (!file) return;
    mkdirSync(dirname(file), { recursive: true });
    const data = { version: 1, issuer };
    for (const name of names) data[name] = [...maps[name]].filter(([, value]) => value.expires > Date.now());
    writeFileSync(file + '.tmp', JSON.stringify(data), { mode: 0o600 });
    renameSync(file + '.tmp', file);
  };
  return { ...maps, persist };
}
