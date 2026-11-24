// Config plugin: permissions + foreground service declaration. No network permission is added.
const { withAndroidManifest } = require('@expo/config-plugins');

const PERMS = [
  'android.permission.PACKAGE_USAGE_STATS',
  'android.permission.SYSTEM_ALERT_WINDOW',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
  'android.permission.POST_NOTIFICATIONS',
  'android.permission.QUERY_ALL_PACKAGES',
  'android.permission.KILL_BACKGROUND_PROCESSES',
];

module.exports = (config) =>
  withAndroidManifest(config, (c) => {
    const m = c.modResults.manifest;
    m.$['xmlns:tools'] = 'http://schemas.android.com/tools';
    m['uses-permission'] = m['uses-permission'] || [];
    for (const p of PERMS) {
      if (m['uses-permission'].some((x) => x.$['android:name'] === p)) continue;
      const e = { $: { 'android:name': p } };
      if (p.endsWith('PACKAGE_USAGE_STATS')) e.$['tools:ignore'] = 'ProtectedPermissions';
      m['uses-permission'].push(e);
    }
    const app = m.application[0];
    app.service = app.service || [];
    if (!app.service.some((s) => s.$['android:name'].endsWith('MonitorService'))) {
      app.service.push({
        $: {
          'android:name': 'expo.modules.usageguard.MonitorService',
          'android:exported': 'false',
          'android:foregroundServiceType': 'specialUse',
        },
        property: [{ $: { 'android:name': 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE', 'android:value': 'screen_time_monitor' } }],
      });
    }
    return c;
  });
