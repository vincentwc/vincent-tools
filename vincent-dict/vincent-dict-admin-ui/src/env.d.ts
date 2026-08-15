/// <reference types="vite/client" />

interface VinDictConfig {
  apiPath?: string;
  historyBase?: string;
}

interface Window {
  __VIN_DICT_CONFIG__?: VinDictConfig;
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<object, object, unknown>;
  export default component;
}
