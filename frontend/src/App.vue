<template>
  <router-view />
  <GlobalNotice />
</template>

<script setup>
import { useTheme } from './composables/theme'
import GlobalNotice from './components/GlobalNotice.vue'

useTheme()
</script>
