<script setup>
defineProps({
  selected: String,
  list: { type: Array, default: () => [] }
});
defineEmits(['select']);
</script>

<template>
  <div class="action-grid">
    <button
      v-for="a in list"
      :key="a.code"
      :class="['action-card', { selected: selected === a.code }]"
      @click="$emit('select', a.code)"
    >
      <div class="icon">
        <svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path :d="a.icon" />
        </svg>
      </div>
      <div class="meta">
        <div class="name">{{ a.label }}</div>
        <div class="desc">{{ a.desc }}</div>
      </div>
      <div class="check" v-show="selected === a.code">
        <svg viewBox="0 0 20 20" fill="currentColor"><path d="M16.7 5.3a1 1 0 0 1 0 1.4l-8 8a1 1 0 0 1-1.4 0l-4-4a1 1 0 1 1 1.4-1.4L8 12.6l7.3-7.3a1 1 0 0 1 1.4 0z"/></svg>
      </div>
    </button>
  </div>
</template>

<style scoped>
.action-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.action-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg-card);
  border: 1.5px solid var(--border);
  border-radius: 12px;
  text-align: left;
  transition: all var(--transition);
  overflow: hidden;
}
.action-card:hover { border-color: var(--border-hover); }
.action-card.selected {
  border-color: var(--cyan);
  background: var(--cyan-dim);
}
.action-card.selected::before {
  content: '';
  position: absolute; inset: 0;
  background: var(--grad-primary);
  opacity: .06;
  pointer-events: none;
}
.icon {
  width: 36px; height: 36px;
  background: var(--bg-card-2);
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  color: var(--text-2);
  flex-shrink: 0;
}
.icon svg { width: 20px; height: 20px; }
.action-card.selected .icon { color: var(--cyan); background: rgba(0, 240, 255, .08); }

.meta { flex: 1; min-width: 0; }
.name { font-size: 13px; font-weight: 700; color: var(--text); margin-bottom: 1px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.desc { font-size: 10px; color: var(--text-3); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.check {
  width: 20px; height: 20px;
  border-radius: 50%;
  background: var(--cyan);
  color: #0a0a0f;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  animation: scaleIn .3s;
}
.check svg { width: 12px; height: 12px; }
</style>
