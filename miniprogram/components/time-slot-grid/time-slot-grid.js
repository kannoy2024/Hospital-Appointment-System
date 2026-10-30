Component({
  properties: {
    morning: { type: Array, value: [] },
    afternoon: { type: Array, value: [] },
    selected: { type: String, value: '' },
    hasAnySchedule: { type: Boolean, value: false },
    hasAvailable: { type: Boolean, value: false }
  },
  methods: {
    // 选择某个可预约的时段，向父组件抛出 select 事件并带上时段与排班 id
    onSelect(e) {
      const { time, available, scheduleId } = e.currentTarget.dataset
      if (!available) return
      this.triggerEvent('select', { time, scheduleId })
    }
  }
})
