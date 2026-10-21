import { writeFileSync, existsSync } from 'node:fs';
import { randomToken, validateConfig } from './config.js';

const publicUrl = process.argv[2];
if (!publicUrl) { console.error('Usage : npm run setup -- https://mcp.votre-domaine.fr'); process.exit(1); }
if (existsSync('.env')) { console.error('.env existe déjà. Modifiez-le ou déplacez-le avant de refaire l’appairage.'); process.exit(1); }
const config = validateConfig({ publicUrl, phoneToken: randomToken(), adminPassword: randomToken() });
writeFileSync('.env', `PUBLIC_URL=${config.publicUrl}\nPHONE_TOKEN=${config.phoneToken}\nADMIN_PASSWORD=${config.adminPassword}\nBIND_ADDRESS=127.0.0.1\nPORT=8787\n`, { mode: 0o600, flag: 'wx' });
console.log('Configuration créée dans server/.env (accès privé). Ouvrez ce fichier localement pour appairer le téléphone et autoriser ChatGPT. Ne partagez pas ce fichier.');
