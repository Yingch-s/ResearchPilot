import { createRouter, createWebHistory } from 'vue-router'
import Home from './views/Home.vue'
import Workspace from './views/Workspace.vue'
import SharedReport from './views/SharedReport.vue'
import Settings from './views/Settings.vue'
import Prototype from './views/Prototype.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: Home },
    { path: '/notebooks/:topicId', component: Workspace, beforeEnter: to =>
      !['null', 'undefined'].includes(String(to.params.topicId)) ? true : '/' },
    { path: '/shared/:token', component: SharedReport },
    { path: '/settings', component: Settings },
    { path: '/prototype', redirect: '/prototype/home' },
    { path: '/prototype/:view(home|quick|deep)', component: Prototype }
  ]
})

export default router
