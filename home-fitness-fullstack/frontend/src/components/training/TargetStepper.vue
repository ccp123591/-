<script setup>
const props = defineProps({
  modelValue: { type: Number, default: 10 },
  min: { type: Number, default: 1 },
  max: { type: Number, default: 200 },
  label: { type: String, default: '目标次数' }
});
const emit = defineEmits(['update:modelValue']);

function minus() { emit('update:modelValue', Math.max(props.min, (props.modelValue || 0) - 1)); }
function plus()  { emit('update:modelValue', Math.min(props.max, (props.modelValue || 0) + 1)); }
function onInput(e) {
  const v = parseInt(e.target.value) || props.min;
  emit('update:modelValue', Math.max(props.min, Math.min(props.max, v)));
}
</script>

<template>
  <div class="stepper-row">
    <div class="left">
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
        <circle cx="10" cy="10" r="7"/>
        <circle cx="10" cy="10" r="3"/>
        <circle cx="10" cy="10" r="1" fill="currentColor"/>
      </svg>
      <span class="lbl">{{ label }}</span>
    </div>
    <div class="stepper">
      <button class="sb" @click="minus" type="button">
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="4" y1="8" x2="12" y2="8"/></svg>
      </button>
      <input type="number" :value="modelValue" :min="min" :max="max" @input="onInput" />
      <button class="sb" @click="plus" type="button">
        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="8" y1="4" x2="8" y2="12"/><line x1="4" y1="8" x2="12" y2="8"/></svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.stepper-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
}
.left {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-2);
}
.left svg { width: 18px; height: 18px; }
.lbl { font-size: 13px; font-weight: 500; }

.stepper {
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--bg-card-2);
  border-radius: 10px;
  padding: 3px;
}
.sb {
  width: 32px; height: 32px;
  border-radius: 8px;
  background: var(--bg-elevated);
  color: var(--text-2);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition);
}
.sb:hover { color: var(--cyan); }
.sb:active { transform: scale(.92); }
.sb svg { width: 14px; height: 14px; }

.stepper input {
  width: 48px;
  padding: 0;
  text-align: center;
  background: transparent;
  border: none;
  color: var(--text);
  font-weight: 700;
  font-size: 15px;
  outline: none;
  -moz-appearance: textfield;
}
.stepper input::-webkit-outer-spin-button,
.stepper input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
</style>
