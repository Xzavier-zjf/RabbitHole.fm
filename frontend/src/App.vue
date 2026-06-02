<template>
  <router-view />
  <MiniPlayer />
  <GlobalNotice />
</template>

<script setup>
import { useTheme } from './composables/theme'
import GlobalNotice from './components/GlobalNotice.vue'
import MiniPlayer from './components/MiniPlayer.vue'

useTheme()
</script>
