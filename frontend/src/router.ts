import { createRouter, createWebHistory } from 'vue-router';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('./pages/HomePage.vue') },
    {
      path: '/studio/:workspaceId/:projectId',
      name: 'studio',
      component: () => import('./pages/StudioPage.vue'),
    },
    { path: '/assets', name: 'assets', component: () => import('./pages/AssetsPage.vue') },
    { path: '/canvas', name: 'canvas', component: () => import('./pages/CanvasPage.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
});
