import { createRouter, createWebHistory } from 'vue-router';
import DictDetailView from './views/DictDetailView.vue';
import DictListView from './views/DictListView.vue';

function resolveHistoryBase(): string {
  const configured = globalThis.window?.__VIN_DICT_CONFIG__?.historyBase;
  if (configured && configured.length > 0) {
    return configured;
  }
  return '/';
}

export const router = createRouter({
  history: createWebHistory(resolveHistoryBase()),
  routes: [
    { path: '/', name: 'dicts', component: DictListView },
    { path: '/dicts/:dictId', name: 'dict-detail', component: DictDetailView },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
});
