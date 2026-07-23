import {ref} from 'vue'

const visible = ref(false)
const title = ref('确认')
const message = ref('')
const okText = ref('确定')
const cancelText = ref('取消')
let resolveFn = null

export function useConfirm() {
  function confirm(opts) {
    const msg = typeof opts === 'string' ? opts : (opts?.message || '')
    title.value = (typeof opts === 'object' && opts.title) ? opts.title : '确认'
    message.value = msg
    okText.value = (typeof opts === 'object' && opts.okText) ? opts.okText : '确定'
    cancelText.value = (typeof opts === 'object' && opts.cancelText) ? opts.cancelText : '取消'
    visible.value = true
    return new Promise(resolve => {
      resolveFn = resolve
    })
  }

  function ok() {
    visible.value = false
    if (resolveFn) resolveFn(true)
  }

  function cancel() {
    visible.value = false
    if (resolveFn) resolveFn(false)
  }

  return { visible, title, message, okText, cancelText, confirm, ok, cancel }
}
