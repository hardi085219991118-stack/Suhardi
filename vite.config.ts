import { defineConfig, Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';
import crypto from 'crypto';

function firmsApiPlugin(): Plugin {
  return {
    name: 'firms-api-middleware',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        if (!req.url) return next();
        const parsedUrl = new URL(req.url, 'http://localhost:3000');

        if (parsedUrl.pathname === '/api/health') {
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify({
            status: 'ok',
            service: 'Hardi Mantangai Fire Now',
            timestamp: Date.now(),
            hasEnvMapKey: Boolean(process.env.FIRMS_MAP_KEY && process.env.FIRMS_MAP_KEY.trim().length > 0)
          }));
          return;
        }

        if (parsedUrl.pathname === '/api/firms/area') {
          const requestTime = Date.now();
          try {
            const userHeaderKey = (req.headers['x-map-key'] as string) || '';
            const userQueryKey = parsedUrl.searchParams.get('mapKey') || '';
            const envKey = process.env.FIRMS_MAP_KEY || '';
            const effectiveKey = (userHeaderKey.trim() || userQueryKey.trim() || envKey.trim());

            if (!effectiveKey) {
              res.statusCode = 401;
              res.setHeader('Content-Type', 'application/json');
              res.end(JSON.stringify({
                state: 'API_CREDENTIAL_REQUIRED',
                error: {
                  code: 'MISSING_CREDENTIAL',
                  message: 'MAP_KEY NASA FIRMS belum dikonfigurasi. Silakan masukkan MAP_KEY resmi Anda.',
                  recoveryAction: 'Buka pengaturan dan masukkan MAP_KEY dari https://firms.modaps.eosdis.nasa.gov/api/api_key/'
                },
                requestTimeMillis: requestTime,
                fetchTimeMillis: Date.now(),
              }));
              return;
            }

            const source = parsedUrl.searchParams.get('source') || 'VIIRS_NOAA21_NRT';
            const bbox = parsedUrl.searchParams.get('bbox') || '113.5,-3.5,115.0,-2.0';
            const dayRange = parseInt(parsedUrl.searchParams.get('dayRange') || '1', 10);

            const firmsUrl = `https://firms.modaps.eosdis.nasa.gov/api/area/csv/${effectiveKey}/${source}/${bbox}/${dayRange}`;
            const response = await fetch(firmsUrl, {
              headers: {
                'User-Agent': 'HardiMantangaiFire/1.0 (Web; ZeroDummy)',
                'Accept': 'text/csv, text/plain, */*'
              }
            });

            const statusCode = response.status;
            const responseText = await response.text();
            const fetchTime = Date.now();
            const hash = crypto.createHash('sha256').update(responseText).digest('hex');

            if (responseText.toLowerCase().includes('invalid map_key')) {
              res.statusCode = 401;
              res.setHeader('Content-Type', 'application/json');
              res.end(JSON.stringify({
                state: 'API_CREDENTIAL_REQUIRED',
                httpStatusCode: 401,
                error: {
                  code: 'INVALID_MAP_KEY',
                  message: 'NASA FIRMS menolak kredensial (Invalid MAP_KEY). Periksa kunci API Anda.',
                  recoveryAction: 'Periksa MAP_KEY pada halaman akun NASA FIRMS Anda.'
                },
                responseSha256Hash: hash,
                requestTimeMillis: requestTime,
                fetchTimeMillis: fetchTime,
                sourceSensor: source
              }));
              return;
            }

            res.statusCode = statusCode;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({
              state: response.ok ? 'SUCCESS' : (statusCode === 429 ? 'RATE_LIMIT_EXCEEDED' : 'DATA_SOURCE_UNAVAILABLE'),
              httpStatusCode: statusCode,
              sourceSensor: source,
              requestArea: bbox,
              requestDayRange: dayRange,
              responseSha256Hash: hash,
              responseByteCount: Buffer.byteLength(responseText, 'utf8'),
              requestTimeMillis: requestTime,
              fetchTimeMillis: fetchTime,
              csvContent: responseText,
              credentialType: (userHeaderKey.trim() || userQueryKey.trim()) ? 'USER_PROVIDED' : 'SERVER_ENV'
            }));
            return;
          } catch (error: any) {
            res.statusCode = 502;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({
              state: 'NETWORK_ERROR',
              error: {
                code: 'NETWORK_FAILED',
                message: `Gagal terhubung ke NASA FIRMS: ${error?.message || 'Network error'}`
              },
              requestTimeMillis: requestTime,
              fetchTimeMillis: Date.now(),
            }));
            return;
          }
        }

        next();
      });
    }
  };
}

export default defineConfig({
  plugins: [react(), tailwindcss(), firmsApiPlugin()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    host: '0.0.0.0',
  },
});
