import { createRouter, createWebHistory } from 'vue-router'
import RadioView from './views/RadioView.vue'
import LoginView from './views/LoginView.vue'
import HistoryView from './views/HistoryView.vue'
import FavoritesView from './views/FavoritesView.vue'
import RequestView from './views/RequestView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: RadioView },
    { path: '/login', component: LoginView },
    { path: '/history', component: HistoryView },
    { path: '/favorites', component: FavoritesView },
    { path: '/request', component: RequestView },
  ],
})

export default router
