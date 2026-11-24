import { requireNativeModule } from 'expo-modules-core';

export type AppInfo = { packageName: string; label: string; icon: string };
export type Config = {
  enabled: boolean; packages: string[]; limitMin: number; message: string;
  imagePath: string; audioPath: string; audioName: string; stamp: number;
};

type Native = {
  hasUsagePermission(): boolean;
  hasOverlayPermission(): boolean;
  openUsageSettings(): void;
  openOverlaySettings(): void;
  getInstalledApps(): Promise<AppInfo[]>;
  getConfig(): string;
  saveConfig(json: string): void;
  copyToInternal(uri: string, name: string): Promise<string>;
  testOverlay(): void;
  getStats(): string;
  clearStats(): void;
};

export default requireNativeModule<Native>('UsageGuard');
