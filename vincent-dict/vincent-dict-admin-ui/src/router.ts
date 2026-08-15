import { defineComponent, h } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';

const Placeholder = defineComponent({
  name: 'Placeholder',
  setup() {
    return () => h('div');
  }
});

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dicts', component: Placeholder },
    { path: '/dicts/:dictId', name: 'dict-detail', component: Placeholder },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
});
