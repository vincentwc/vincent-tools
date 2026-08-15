import { createRouter, createWebHistory } from 'vue-router';
import AuditListView from './views/AuditListView.vue';

function resolveHistoryBase(): string {
  const configured = globalThis.window?.__VIN_AUDIT_CONFIG__?.historyBase;
  if (configured && configured.length > 0) {
    return configured;
  }
  return '/';
}

export const router = createRouter({
  history: createWebHistory(resolveHistoryBase()),
  routes: [
    { path: '/', name: 'audits', component: AuditListView },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
});
