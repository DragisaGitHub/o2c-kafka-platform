<template>
  <div>
    <label :for="id">{{ label }}</label>
    <div>
      <input
        :id="id"
        :name="name ?? id"
        :type="type"
        :inputmode="inputMode"
        :placeholder="placeholder"
        :autocomplete="autocomplete"
        :value="modelValue"
        :aria-invalid="hasError ? 'true' : 'false'"
        :aria-describedby="hasError ? errorId : undefined"
        @input="onInput"
      />
    </div>
    <div v-if="hasError" :id="errorId" role="alert">
      <slot name="error" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type InputMode = 'none' | 'text' | 'tel' | 'url' | 'email' | 'numeric' | 'decimal' | 'search'

const props = defineProps<{
  id: string
  label: string
  modelValue: string
  name?: string
  type?: string
  inputMode?: InputMode
  placeholder?: string
  autocomplete?: string
  hasError?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const type = computed(() => props.type ?? 'text')
const hasError = computed(() => props.hasError ?? false)
const errorId = computed(() => `${props.id}-error`)

function onInput(e: Event) {
  const target = e.target as HTMLInputElement
  emit('update:modelValue', target.value)
}
</script>
