import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export const useTrainingStore = defineStore('training', () => {
  const action = ref('squat');
  const targetReps = ref(10);
  const reps = ref(0);
  const score = ref(0);
  const rhythmScore = ref(0);
  const stabilityScore = ref(0);
  const depthScore = ref(0);
  const symmetryScore = ref(0);
  const currentAngle = ref(null);
  const elapsedMs = ref(0);
  const isTraining = ref(false);
  const isPaused = ref(false);
  const statusText = ref('');

  const progress = computed(() => {
    if (!targetReps.value) return 0;
    return Math.min(100, Math.round(reps.value / targetReps.value * 100));
  });

  function reset() {
    reps.value = 0;
    score.value = 0;
    rhythmScore.value = 0;
    stabilityScore.value = 0;
    depthScore.value = 0;
    symmetryScore.value = 0;
    currentAngle.value = null;
    elapsedMs.value = 0;
    statusText.value = '';
  }

  return {
    action, targetReps, reps,
    score, rhythmScore, stabilityScore, depthScore, symmetryScore,
    currentAngle, elapsedMs, isTraining, isPaused, statusText,
    progress, reset
  };
});
