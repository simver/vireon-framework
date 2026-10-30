'use strict';
const {assertBrowserExecutionAllowed,launchChromium}=require('./browser-safety.cjs');
assertBrowserExecutionAllowed(); // before output, servers, fixtures or browser loading
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const crypto = require('node:crypto');

const output = process.env.SCALAR_CSP_OUTPUT;
assert.ok(output && path.isAbsolute(output) && !fs.existsSync(output), 'new absolute output required');
fs.mkdirSync(output);
const assets = path.resolve(__dirname, '../../src/main/resources/META-INF/vireon/openapi');
const playwright = require(process.env.PLAYWRIGHT_ROOT);
const policy = "default-src 'none'; script-src 'self'; connect-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; object-src 'none'; base-uri 'none'";
const schema = { openapi: '3.1.0', info: { title: 'CSP probe', version: '1' }, servers: [{ url: '/' }], paths: {
  '/probe': {
    get: { summary: 'Read probe', responses: { 200: { description: 'OK' } } },
    put: { summary: 'Write probe', requestBody: { content: { 'application/json': { schema: { type: 'object', properties: { displayName: { type: 'string' } } }, example: { displayName: 'probe' } } } }, responses: { 200: { description: 'OK' } } }
  }
} };
const server = http.createServer((request, response) => {
  const url = new URL(request.url, 'http://127.0.0.1');
  response.setHeader('Content-Security-Policy', policy);
  response.setHeader('Content-Type', 'application/json');
  if (url.pathname === '/scalar.js') {
    response.setHeader('Content-Type', 'application/javascript');
    return fs.createReadStream(path.join(assets, 'scalar.js')).pipe(response);
  }
  if (url.pathname === '/styles.css') {
    response.setHeader('Content-Type', 'text/css');
    return response.end(Buffer.concat([fs.readFileSync(path.join(assets, 'scalar.css')), Buffer.from('\n'), fs.readFileSync(path.join(assets, 'styles.css'))]));
  }
  if (url.pathname === '/bootstrap.js') {
    response.setHeader('Content-Type', 'application/javascript');
    const config = { url: '/openapi', telemetry: false, withDefaultFonts: false, persistAuth: false, hideClientButton: true, hideTestRequestButton: url.searchParams.get('enabled') !== 'true', agent: { disabled: true }, mcp: { disabled: true }, showDeveloperTools: 'never' };
    return response.end(`Scalar.createApiReference('#app',${JSON.stringify(config)})`);
  }
  if (url.pathname === '/openapi') return response.end(JSON.stringify(schema));
  if (url.pathname === '/probe') {
    if (request.method === 'GET') return response.end('{"success":true}');
    let body = '';
    request.on('data', chunk => { body += chunk; });
    return request.on('end', () => {
      if (request.headers['x-xsrf-token'] !== 'probe-token') { response.statusCode = 403; return response.end('{}'); }
      response.end(body);
    });
  }
  response.setHeader('Content-Type', 'text/html;charset=UTF-8');
  response.end(`<!doctype html><meta charset="UTF-8"><link rel="icon" href="data:,"><link rel="stylesheet" href="/styles.css"><div id="app"></div><script src="/scalar.js"></script><script src="/bootstrap.js?enabled=${url.searchParams.get('enabled') === 'true'}"></script>`);
});

(async () => {
  const receipt = { status: 'FAIL', browsers: [], assets: Object.fromEntries(['scalar.js', 'scalar.css'].map(file => [file, crypto.createHash('sha256').update(fs.readFileSync(path.join(assets, file))).digest('hex')])) };
  try {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const origin = 'http://127.0.0.1:' + server.address().port;
    for (const engine of ['firefox', 'chromium']) {
      let browser;
      try {
        browser = await (engine === 'chromium' ? launchChromium(playwright.chromium,require.resolve(process.env.PLAYWRIGHT_ROOT+'/package.json'),{headless:true,executablePath:process.env.CHROME_PATH}) : playwright.firefox.launch({headless:true}));
        const page = await browser.newPage();
        const errors = [], external = [], consoleDetails = [];
        page.on('pageerror', error => errors.push(error.stack || error.message));
        page.on('console', message => { if (message.type() === 'error') { errors.push(message.text()); consoleDetails.push(Promise.all(message.args().map(argument => argument.evaluate(value => value?.stack || value?.message || String(value)).catch(() => 'unavailable')))); } });
        page.on('request', request => { if (!request.url().startsWith(origin + '/')) external.push(request.url()); });
        await page.addInitScript(() => { window.cspViolations = []; document.addEventListener('securitypolicyviolation', event => window.cspViolations.push(event.violatedDirective)); });
        await page.goto(origin, { waitUntil: 'networkidle' });
        await page.getByRole('heading', { name: 'Read probe', exact: true }).waitFor();
        assert.equal(await page.getByRole('button', { name: /Test Request/ }).count(), 0);
        assert.deepEqual(await page.evaluate(() => window.cspViolations), []);
        await page.goto(origin + '/?enabled=true', { waitUntil: 'networkidle' });
        await page.getByRole('button', { name: /Test Request.*get/ }).click();
        const [result] = await Promise.all([
          page.waitForResponse(response => response.url() === origin + '/probe' && response.request().method() === 'GET'),
          page.getByRole('button', { name: /Send Request/ }).click()
        ]);
        assert.equal(result.status(), 200);
        await page.getByRole('button', { name: 'Close Client', exact: true }).click();
        await page.getByRole('button', { name: /Test Request.*put/ }).click();
        await page.getByRole('combobox', { name: 'undefined Key', exact: true }).nth(1).fill('X-XSRF-TOKEN');
        await page.getByRole('checkbox', { name: 'Include X-XSRF-TOKEN in request', exact: true }).waitFor();
        await page.getByRole('combobox', { name: 'undefined Value', exact: true }).nth(1).fill('probe-token');
        await page.waitForTimeout(400);
        await page.locator('.cm-content').first().click();
        await page.keyboard.press('ControlOrMeta+A');
        await page.keyboard.insertText('{"displayName":"changed"}');
        await page.waitForTimeout(400);
        const [written] = await Promise.all([
          page.waitForResponse(response => response.url() === origin + '/probe' && response.request().method() === 'PUT'),
          page.getByRole('button', { name: /Send Request/ }).click()
        ]);
        assert.equal(written.status(), 200);
        assert.equal((await written.json()).displayName, 'changed');
        if (errors.length) receipt.consoleDetails = await Promise.all(consoleDetails);
        assert.deepEqual(errors, []);
        assert.deepEqual(external, []);
        assert.deepEqual(await page.evaluate(() => window.cspViolations), []);
        receipt.browsers.push({ engine, version: browser.version(), defaultHidden: 'PASS', read: 'PASS', write: 'PASS', cspViolations: 0, externalRequests: 0, scriptErrors: 0 });
      } finally { await browser?.close(); }
    }
    receipt.status = 'PASS';
  } catch (error) { receipt.failure = error.message.slice(0,1600); }
  finally {
    server.closeAllConnections(); await new Promise(resolve => server.close(resolve));
    fs.writeFileSync(path.join(output, 'receipt.json'), JSON.stringify(receipt, null, 2));
    console.log('SCALAR_CSP_' + receipt.status); process.exitCode = receipt.status === 'PASS' ? 0 : 1;
  }
})();
