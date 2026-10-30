'use strict';
// Same reviewed bytes are kept inside each independent owner; no sibling source dependency.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {createRequire} = require('node:module');
const FEATURE = 'AutofillAiWalletPrivatePasses';
const POLICY = 'vireon-browser-safety-20261020-1';
const REVIEWED = Object.freeze({
  version: '1.58.2',
  files: Object.freeze({
    'lib/server/browserType.js': '08266f672f1ba9dedd77fb30008c7590e6400dcf9bf497fe3fe61ce0e74bcbf0',
    'lib/server/chromium/chromium.js': '3da2c7355ea37fd89c072680cf559129943730b2a684809b1dbe43d19cabc745',
    'lib/server/chromium/chromiumSwitches.js': '37d1a75c1c4e0cbc02b31d65f3b80d21b1bdf227a09146603e181a6ec54f293a'
  })
});
function check(ok, code) { if (!ok) throw new Error('BROWSER_SAFETY_' + code); }
function digest(file) { return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex'); }
function assertBrowserExecutionAllowed() {
  // No environment variable, caller platform override, retry or profile reuse unlocks Windows.
  check(process.platform !== 'win32', 'WINDOWS_DYNAMIC_VALIDATION_NOT_RELEASED');
}
function inspectPlaywright(packageFile) {
  check(typeof packageFile === 'string' && path.isAbsolute(packageFile), 'ABSOLUTE_PACKAGE_REQUIRED');
  const pkg = JSON.parse(fs.readFileSync(packageFile, 'utf8'));
  check(pkg.name === 'playwright' && pkg.version === REVIEWED.version, 'UNREVIEWED_PLAYWRIGHT');
  const coreFile = createRequire(packageFile).resolve('playwright-core/package.json');
  const core = JSON.parse(fs.readFileSync(coreFile, 'utf8'));
  check(core.name === 'playwright-core' && core.version === pkg.version, 'CORE_VERSION_MISMATCH');
  const coreRoot = path.dirname(coreFile);
  for (const [relative, expected] of Object.entries(REVIEWED.files)) {
    check(digest(path.join(coreRoot, relative)) === expected, 'CORE_SOURCE_DRIFT');
  }
  return {packageFile, coreFile, coreRoot, version: pkg.version, packageSha256: digest(packageFile), coreSources: REVIEWED.files};
}
function featureList(value) {
  const values = value.split(',').map(value => value.trim());
  check(values.length > 0 && values.every(value => /^[A-Za-z][A-Za-z0-9_.-]*$/.test(value)), 'MALFORMED_FEATURE_LIST');
  return values;
}
function mergeFeatureFlags(defaultArgs, userArgs = []) {
  check(Array.isArray(defaultArgs) && Array.isArray(userArgs), 'ARG_ARRAY_REQUIRED');
  const defaults = defaultArgs.filter(arg => arg.startsWith('--disable-features='));
  check(defaults.length > 0, 'DEFAULT_FEATURE_FLAG_MISSING');
  const all = [], remaining = [];
  for (const flag of defaults) all.push(...featureList(flag.slice('--disable-features='.length)));
  for (const arg of userArgs) {
    check(typeof arg === 'string' && arg.startsWith('--') && !arg.includes('\0'), 'MALFORMED_ARGUMENT');
    check(!/^--(?:no-sandbox|disable-.*sandbox|single-process|in-process-gpu)(?:=|$)/.test(arg), 'SANDBOX_BYPASS');
    if (arg.startsWith('--disable-features')) {
      check(arg.startsWith('--disable-features='), 'MALFORMED_FEATURE_SWITCH');
      all.push(...featureList(arg.slice('--disable-features='.length)));
    } else {
      if (arg.startsWith('--enable-features')) {
        check(arg.startsWith('--enable-features='), 'MALFORMED_FEATURE_SWITCH');
        check(!featureList(arg.slice('--enable-features='.length)).includes(FEATURE), 'WORKAROUND_REENABLED');
      }
      remaining.push(arg);
    }
  }
  const features = [...new Set([...all, FEATURE])];
  const flag = '--disable-features=' + features.join(',');
  const sameDefaults = defaults.filter(arg => arg === flag);
  check(sameDefaults.length <= 1, 'AMBIGUOUS_DUPLICATE_DEFAULT');
  // Playwright filters defaultArgs INCLUDING appended user args, so never filter our merged flag.
  const ignoreDefaultArgs = defaults.filter(arg => arg !== flag);
  const args = sameDefaults.length ? remaining : [...remaining, flag];
  check(!ignoreDefaultArgs.includes(flag), 'MERGED_FLAG_WOULD_BE_FILTERED');
  return {args, ignoreDefaultArgs, features};
}
function prepareChromiumOptions(packageFile, options = {}) {
  check(options && typeof options === 'object' && !Array.isArray(options), 'OPTIONS_REQUIRED');
  for (const key of ['ignoreDefaultArgs', 'ignoreAllDefaultArgs', 'assistantMode', 'cdpPort', 'proxyOverride']) {
    check(!Object.hasOwn(options, key), 'CALLER_OVERRIDE_' + key);
  }
  check(options.chromiumSandbox !== false, 'SANDBOX_REQUIRED');
  check(options.executablePath === undefined || path.isAbsolute(options.executablePath), 'ABSOLUTE_EXECUTABLE_REQUIRED');
  const source = inspectPlaywright(packageFile);
  const {chromiumSwitches} = require(path.join(source.coreRoot, 'lib/server/chromium/chromiumSwitches.js'));
  const defaults = chromiumSwitches(false, options.channel);
  const merged = mergeFeatureFlags(defaults, options.args);
  return {options: {...options, chromiumSandbox: true, args: merged.args, ignoreDefaultArgs: merged.ignoreDefaultArgs},
    proof: {policy: POLICY, ...source, defaultArgs: defaults, disabledFeatures: merged.features,
      scope: 'static launch options; not actual process argv or Windows Security-log validation'}};
}
function assertChromiumIdentity(browserType, packageFile) {
  inspectPlaywright(packageFile);
  const actual = require(path.dirname(packageFile)).chromium;
  check(browserType === actual && browserType.name() === 'chromium', 'BROWSER_OBJECT_MISMATCH');
}
async function launchChromium(browserType, packageFile, options) {
  assertBrowserExecutionAllowed();
  assertChromiumIdentity(browserType, packageFile);
  return browserType.launch(prepareChromiumOptions(packageFile, options).options);
}
async function launchChromiumPersistent(browserType, packageFile, profile, options) {
  assertBrowserExecutionAllowed();
  check(typeof profile === 'string' && path.isAbsolute(profile), 'ABSOLUTE_PROFILE_REQUIRED');
  assertChromiumIdentity(browserType, packageFile);
  return browserType.launchPersistentContext(profile, prepareChromiumOptions(packageFile, options).options);
}
module.exports = {POLICY, FEATURE, REVIEWED, assertBrowserExecutionAllowed, inspectPlaywright,
  mergeFeatureFlags, prepareChromiumOptions, assertChromiumIdentity, launchChromium, launchChromiumPersistent};
