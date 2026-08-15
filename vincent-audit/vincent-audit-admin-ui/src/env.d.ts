/// <reference types="vite/client" />

interface VinAuditConfig {
  apiPath?: string;
  historyBase?: string;
}

interface Window {
  __VIN_AUDIT_CONFIG__?: VinAuditConfig;
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<object, object, unknown>;
  export default component;
}
