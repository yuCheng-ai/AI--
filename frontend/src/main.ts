import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import { router } from './router';
import { useAppStore } from './stores/app';
import './styles/tokens.css';
import './styles/base.css';
import './styles/focus.css';

const application = createApp(App);
application.use(createPinia());
const state = useAppStore();
// A deployment can retire a lazy-loaded chunk while an older tab is open.
// Preserve the current editor and let the user refresh instead of reloading silently.
window.addEventListener('vite:preloadError', () => {
  state.resourceError = true;
});
router.onError(() => {
  state.resourceError = true;
});
router.afterEach((_, __, failure) => {
  if (!failure) state.resourceError = false;
});
application.use(router).mount('#app');
