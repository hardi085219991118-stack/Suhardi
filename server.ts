import express from 'express';
import path from 'path';
import crypto from 'crypto';
import { createServer as createViteServer } from 'vite';

const app = express();
const PORT = 3000;

app.use(express.json());

// API health endpoint
app.get('/api/health', (req, res) => {
  res.json({
    status: 'ok',
    service: 'Hardi Mantangai Fire Now Backend',
    timestamp: Date.now(),
    hasEnvMapKey: Boolean(process.env.FIRMS_MAP_KEY && process.env.FIRMS_MAP_KEY.trim().length > 0)
  });
});

// NASA FIRMS Proxy Route to bypass browser CORS and provide server-level SHA256 audit
app.get('/api/firms/area', async (req, res) => {
  const requestTime = Date.now();
  try {
    const userHeaderKey = (req.headers['x-map-key'] as string) || '';
    const userQueryKey = (req.query.mapKey as string) || '';
    const envKey = process.env.FIRMS_MAP_KEY || '';
    
    // Priority: Header > Query > Env
    const effectiveKey = (userHeaderKey.trim() || userQueryKey.trim() || envKey.trim());

    if (!effectiveKey) {
      return res.status(401).json({
        state: 'API_CREDENTIAL_REQUIRED',
        error: {
          code: 'MISSING_CREDENTIAL',
          message: 'MAP_KEY NASA FIRMS belum dikonfigurasi. Silakan masukkan MAP_KEY resmi Anda.',
          recoveryAction: 'Buka pengaturan dan masukkan MAP_KEY dari https://firms.modaps.eosdis.nasa.gov/api/api_key/'
        },
        requestTimeMillis: requestTime,
        fetchTimeMillis: Date.now(),
      });
    }

    const source = (req.query.source as string) || 'VIIRS_NOAA21_NRT';
    const bbox = (req.query.bbox as string) || '113.5,-3.5,115.0,-2.0';
    const dayRange = parseInt((req.query.dayRange as string) || '1', 10);

    // Validate bbox format (minX, minY, maxX, maxY)
    const bboxParts = bbox.split(',').map(p => parseFloat(p.trim()));
    if (bboxParts.length !== 4 || bboxParts.some(n => isNaN(n))) {
      return res.status(400).json({
        state: 'INVALID_DATA_RESPONSE',
        error: {
          code: 'INVALID_BBOX',
          message: `Format bounding box '${bbox}' tidak valid. Harus 4 nilai koordinat terpisah koma.`,
        }
      });
    }

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

    // Compute SHA-256 hash of response for evidence-based verification audit
    const hash = crypto.createHash('sha256').update(responseText).digest('hex');

    // Check if NASA returned Invalid MAP_KEY
    if (responseText.toLowerCase().includes('invalid map_key')) {
      return res.status(401).json({
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
      });
    }

    if (!response.ok) {
      return res.status(statusCode).json({
        state: statusCode === 429 ? 'RATE_LIMIT_EXCEEDED' : (statusCode === 401 || statusCode === 403 ? 'API_CREDENTIAL_REQUIRED' : 'DATA_SOURCE_UNAVAILABLE'),
        httpStatusCode: statusCode,
        error: {
          code: `HTTP_${statusCode}`,
          message: `NASA FIRMS merespons dengan HTTP ${statusCode}: ${responseText.slice(0, 150)}`
        },
        responseSha256Hash: hash,
        requestTimeMillis: requestTime,
        fetchTimeMillis: fetchTime,
        sourceSensor: source
      });
    }

    return res.json({
      state: 'SUCCESS',
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
    });

  } catch (error: any) {
    const fetchTime = Date.now();
    return res.status(502).json({
      state: 'NETWORK_ERROR',
      error: {
        code: 'NETWORK_FAILED',
        message: `Gagal terhubung ke NASA FIRMS: ${error?.message || 'Network error'}`
      },
      requestTimeMillis: requestTime,
      fetchTimeMillis: fetchTime,
    });
  }
});

async function startServer() {
  // Vite middleware in development
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Hardi Mantangai Fire Now Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
