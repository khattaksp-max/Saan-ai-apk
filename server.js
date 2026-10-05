const http = require('http');
const fs = require('fs');
const path = require('path');
const https = require('https');

const PORT = process.env.PORT || 3000;
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';

const MIME_TYPES = {
    '.html': 'text/html',
    '.css': 'text/css',
    '.js': 'application/javascript',
    '.json': 'application/json',
    '.png': 'image/png',
    '.svg': 'image/svg+xml',
    '.wav': 'audio/wav',
    '.mp3': 'audio/mpeg'
};

const server = http.createServer((req, res) => {
    // CORS headers
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    const parsedUrl = new URL(req.url, `http://${req.headers.host}`);

    // API route to proxy Gemini calls securely using environment GEMINI_API_KEY
    if (parsedUrl.pathname === '/api/gemini' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', () => {
            handleGeminiProxy(body, req, res);
        });
        return;
    }

    // API route to get project status and file list
    if (parsedUrl.pathname === '/api/project-info') {
        const info = {
            appName: 'SANA',
            tagline: 'Intelligent AI Voice Assistant & Personal Companion',
            architecture: 'Android Jetpack Compose + Kotlin + Gradle + Gemini Native Audio',
            githubWorkflow: '.github/workflows/build-apk.yml',
            gradleFiles: [
                'app/build.gradle.kts',
                'build.gradle.kts',
                'settings.gradle.kts',
                'gradlew',
                'app/src/main/AndroidManifest.xml'
            ],
            features: [
                'Real Gemini Native Audio (Kore & Aoede voices)',
                'One-click continuous voice loop with automated VAD',
                'Prevent self-listening with audio focus & muting',
                'English, Urdu, and Roman Urdu language detection',
                'Assistant, Friend, Companion, and Romantic personas',
                'WhatsApp, YouTube, Camera, Settings Android Intents'
            ]
        };
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(info, null, 2));
        return;
    }

    // Serve static files
    let filePath = path.join(__dirname, 'public', parsedUrl.pathname === '/' ? 'index.html' : parsedUrl.pathname);

    if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
        filePath = path.join(__dirname, 'public', 'index.html');
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    fs.readFile(filePath, (err, content) => {
        if (err) {
            res.writeHead(500);
            res.end(`Server Error: ${err.code}`);
        } else {
            res.writeHead(200, { 'Content-Type': contentType });
            res.end(content);
        }
    });
});

function handleGeminiProxy(requestBody, req, res) {
    try {
        const parsed = JSON.parse(requestBody);
        const apiKey = parsed.apiKey || GEMINI_API_KEY;
        const model = parsed.model || 'gemini-2.5-flash-native-audio-preview-12-2025';

        if (!apiKey || apiKey === 'MY_GEMINI_API_KEY') {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
                error: 'Gemini API key is not configured yet. Please configure GEMINI_API_KEY in the Secrets panel or enter it in Settings.'
            }));
            return;
        }

        const payload = JSON.stringify(parsed.payload);
        const options = {
            hostname: 'generativelanguage.googleapis.com',
            path: `/v1beta/models/${model}:generateContent?key=${apiKey}`,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(payload)
            }
        };

        const geminiReq = https.request(options, geminiRes => {
            let data = '';
            geminiRes.on('data', chunk => { data += chunk; });
            geminiRes.on('end', () => {
                res.writeHead(geminiRes.statusCode, { 'Content-Type': 'application/json' });
                res.end(data);
            });
        });

        geminiReq.on('error', err => {
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: `Gemini connection failed: ${err.message}` }));
        });

        geminiReq.write(payload);
        geminiReq.end();
    } catch (e) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: `Invalid request payload: ${e.message}` }));
    }
}

server.listen(PORT, () => {
    console.log(`SANA interactive preview server running on port ${PORT}`);
});
