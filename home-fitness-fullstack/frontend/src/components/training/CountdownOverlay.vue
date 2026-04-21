<script setup>
import { ref } from 'vue';

const show = ref(false);
const num = ref(3);

function run(from = 3) {
  return new Promise(resolve => {
    show.value = true;
    num.value = from;
    const tick = () => {
      setTimeout(() => {
        if (num.value > 1) { num.value--; tick(); }
        else if (num.value === 1) { num.value = 0; tick(); }
        else {
          show.value = false;
          resolve();
        }
      }, 800);
    };
    tick();
  });
}

defineExpose({ run });
</script>

<template>
  <transition name="fade">
    <div v-if="show" class="countdown-overlay">
      <div class="num" :key="num">{{ num === 0 ? 'GO' : num }}</div>
    </div>
  </transition>
</template>

<style scoped>
.countdown-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, .55);
  backdrop-filter: blur(10px);
  z-index: 8;
}
.num {
  font-size: 120px;
  font-weight: 900;
  background: var(--grad-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  filter: drop-shadow(0 0 40px rgba(0, 240, 255, .4));
  animation: countdownPop .8s ease;
}
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
