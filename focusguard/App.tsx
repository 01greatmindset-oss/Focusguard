import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  View, Text, Switch, FlatList, Image, TextInput, TouchableOpacity, StyleSheet,
  ScrollView, PermissionsAndroid, AppState, StatusBar,
} from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import UsageGuard, { AppInfo, Config } from './modules/usage-guard';

const DEFAULTS: Config = {
  enabled: false, packages: [], limitMin: 15, message: "Time's up! Put the phone down.",
  imagePath: '', audioPath: '', audioName: '', stamp: 0,
};
const PRESETS = [5, 10, 15, 30, 45, 60];

export default function App() {
  const [tab, setTab] = useState<'home' | 'custom' | 'stats'>('home');
  const [cfg, setCfg] = useState<Config>(DEFAULTS);
  const [apps, setApps] = useState<AppInfo[]>([]);
  const [query, setQuery] = useState('');
  const [perm, setPerm] = useState({ usage: false, overlay: false });
  const cfgRef = useRef(cfg); cfgRef.current = cfg;
  const loaded = useRef(false);

  const update = useCallback((patch: Partial<Config>) => {
    const next = { ...cfgRef.current, ...patch };
    cfgRef.current = next; setCfg(next);
    UsageGuard.saveConfig(JSON.stringify(next)); // stored locally; (re)starts/stops service
  }, []);

  const refreshPerms = useCallback(() => {
    setPerm({ usage: UsageGuard.hasUsagePermission(), overlay: UsageGuard.hasOverlayPermission() });
    if (loaded.current) UsageGuard.saveConfig(JSON.stringify(cfgRef.current)); // start service once perms granted
  }, []);

  useEffect(() => {
    try { setCfg({ ...DEFAULTS, ...JSON.parse(UsageGuard.getConfig()) }); } catch {}
    loaded.current = true;
    UsageGuard.getInstalledApps().then(setApps);
    PermissionsAndroid.request('android.permission.POST_NOTIFICATIONS' as any).catch(() => {});
    refreshPerms();
    const sub = AppState.addEventListener('change', (s) => s === 'active' && refreshPerms());
    return () => sub.remove();
  }, [refreshPerms]);

  const toggle = (p: string) =>
    update({ packages: cfg.packages.includes(p) ? cfg.packages.filter((x) => x !== p) : [...cfg.packages, p] });

  const pick = async (kind: 'image' | 'audio') => {
    const r = await DocumentPicker.getDocumentAsync({ type: kind === 'image' ? 'image/*' : 'audio/*', copyToCacheDirectory: false });
    if (r.canceled) return;
    const a = r.assets[0];
    const path = await UsageGuard.copyToInternal(a.uri, kind === 'image' ? 'overlay_image' : 'overlay_audio');
    update(kind === 'image' ? { imagePath: path, stamp: Date.now() } : { audioPath: path, audioName: a.name });
  };

  const permsOk = perm.usage && perm.overlay;
  const shown = apps.filter((a) => a.label.toLowerCase().includes(query.toLowerCase()));

  return (
    <View style={s.root}>
      <StatusBar barStyle="light-content" />
      <Text style={s.title}>FocusGuard</Text>

      <View style={s.tabs}>
        {(['home', 'custom', 'stats'] as const).map((t) => (
          <TouchableOpacity key={t} style={[s.tab, tab === t && s.tabOn]} onPress={() => setTab(t)}>
            <Text style={s.tabTxt}>{t === 'home' ? 'Apps' : t === 'custom' ? 'Customize' : 'Stats'}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {tab === 'home' && (
        <FlatList
          data={shown} keyExtractor={(a) => a.packageName}
          ListHeaderComponent={
            <View>
              {!permsOk && (
                <View style={s.card}>
                  <Text style={s.h}>Permissions needed</Text>
                  {!perm.usage && <Btn label="Grant Usage Access" onPress={UsageGuard.openUsageSettings} />}
                  {!perm.overlay && <Btn label="Allow Draw Over Other Apps" onPress={UsageGuard.openOverlaySettings} />}
                </View>
              )}
              <View style={[s.card, s.row]}>
                <Text style={s.h}>Monitoring</Text>
                <Switch value={cfg.enabled} onValueChange={(v) => update({ enabled: v })} />
              </View>
              <View style={s.card}>
                <Text style={s.h}>Daily limit per app: {cfg.limitMin} min</Text>
                <View style={s.row}>
                  <Btn label="−" onPress={() => update({ limitMin: Math.max(1, cfg.limitMin - 1) })} />
                  <View style={s.chips}>
                    {PRESETS.map((m) => (
                      <TouchableOpacity key={m} style={[s.chip, cfg.limitMin === m && s.chipOn]} onPress={() => update({ limitMin: m })}>
                        <Text style={s.tabTxt}>{m}</Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                  <Btn label="+" onPress={() => update({ limitMin: Math.min(1440, cfg.limitMin + 1) })} />
                </View>
              </View>
              <TextInput style={s.input} placeholder="Search apps…" placeholderTextColor="#888" value={query} onChangeText={setQuery} />
            </View>
          }
          renderItem={({ item }) => (
            <View style={[s.card, s.row]}>
              <Image source={{ uri: `data:image/png;base64,${item.icon}` }} style={s.icon} />
              <Text style={[s.txt, { flex: 1 }]} numberOfLines={1}>{item.label}</Text>
              <Switch value={cfg.packages.includes(item.packageName)} onValueChange={() => toggle(item.packageName)} />
            </View>
          )}
        />
      )}

      {tab === 'custom' && (
        <ScrollView contentContainerStyle={{ padding: 12 }}>
          <View style={s.card}>
            <Text style={s.h}>Wallpaper image</Text>
            {!!cfg.imagePath && <Image source={{ uri: `file://${cfg.imagePath}?v=${cfg.stamp}` }} style={s.preview} />}
            <Btn label={cfg.imagePath ? 'Change image' : 'Choose from gallery'} onPress={() => pick('image')} />
          </View>
          <View style={s.card}>
            <Text style={s.h}>Alarm sound</Text>
            <Text style={s.txt}>{cfg.audioName || 'Default alarm tone'}</Text>
            <Btn label="Choose audio file" onPress={() => pick('audio')} />
            {!!cfg.audioPath && <Btn label="Reset to default" onPress={() => update({ audioPath: '', audioName: '' })} />}
          </View>
          <View style={s.card}>
            <Text style={s.h}>Warning message</Text>
            <TextInput style={[s.input, { minHeight: 80 }]} multiline value={cfg.message} onChangeText={(t) => update({ message: t })} />
          </View>
          <Btn label="Test overlay" onPress={() => (perm.overlay ? UsageGuard.testOverlay() : UsageGuard.openOverlaySettings())} />
        </ScrollView>
      )}

      {tab === 'stats' && <Stats apps={apps} />}
    </View>
  );
}

function Stats({ apps }: { apps: AppInfo[] }) {
  const [d, setD] = useState<any>({ usage: {}, log: [] });
  const load = () => { try { setD(JSON.parse(UsageGuard.getStats())); } catch {} };
  useEffect(() => { load(); const t = setInterval(load, 3000); return () => clearInterval(t); }, []);
  const name = (p: string) => apps.find((a) => a.packageName === p)?.label ?? p;
  return (
    <ScrollView contentContainerStyle={{ padding: 12 }}>
      <View style={s.card}>
        <Text style={s.h}>Today</Text>
        {Object.entries(d.usage?.apps ?? {}).map(([p, ms]) => (
          <Text key={p} style={s.txt}>{name(p)} — {Math.round((ms as number) / 60000)} min</Text>
        ))}
      </View>
      <View style={s.card}>
        <Text style={s.h}>Alerts triggered</Text>
        {[...(d.log ?? [])].reverse().map((l: any, i: number) => (
          <Text key={i} style={s.txt}>{new Date(l.t).toLocaleString()} — {name(l.pkg)}</Text>
        ))}
      </View>
      <Btn label="Clear all usage data" onPress={() => { UsageGuard.clearStats(); load(); }} />
    </ScrollView>
  );
}

const Btn = ({ label, onPress }: { label: string; onPress: () => void }) => (
  <TouchableOpacity style={s.btn} onPress={onPress}><Text style={s.btnTxt}>{label}</Text></TouchableOpacity>
);

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0f1115', paddingTop: (StatusBar.currentHeight ?? 24) + 8 },
  title: { color: '#fff', fontSize: 24, fontWeight: '700', paddingHorizontal: 16, paddingBottom: 8 },
  tabs: { flexDirection: 'row', paddingHorizontal: 12, gap: 8 },
  tab: { flex: 1, padding: 10, borderRadius: 10, backgroundColor: '#1b1f27', alignItems: 'center' },
  tabOn: { backgroundColor: '#3b5bdb' },
  tabTxt: { color: '#fff', fontWeight: '600' },
  card: { backgroundColor: '#1b1f27', borderRadius: 12, padding: 12, margin: 6, marginHorizontal: 12, gap: 8 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  h: { color: '#fff', fontSize: 16, fontWeight: '700', flex: 1 },
  txt: { color: '#ddd', fontSize: 15 },
  icon: { width: 40, height: 40, borderRadius: 8 },
  input: { backgroundColor: '#1b1f27', color: '#fff', borderRadius: 10, padding: 12, margin: 12, textAlignVertical: 'top' },
  btn: { backgroundColor: '#3b5bdb', borderRadius: 10, padding: 12, alignItems: 'center', margin: 4 },
  btnTxt: { color: '#fff', fontWeight: '700' },
  chips: { flex: 1, flexDirection: 'row', flexWrap: 'wrap', gap: 6, justifyContent: 'center' },
  chip: { paddingVertical: 6, paddingHorizontal: 10, borderRadius: 8, backgroundColor: '#2a2f3a' },
  chipOn: { backgroundColor: '#3b5bdb' },
  preview: { width: '100%', height: 180, borderRadius: 10 },
});
