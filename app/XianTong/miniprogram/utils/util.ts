import { API_ORIGIN } from './request'

export const formatTime = (date: Date) => {
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = date.getHours()
  const minute = date.getMinutes()
  const second = date.getSeconds()

  return (
    [year, month, day].map(formatNumber).join('/') +
    ' ' +
    [hour, minute, second].map(formatNumber).join(':')
  )
}

const formatNumber = (n: number) => {
  const s = n.toString()
  return s[1] ? s : '0' + s
}


/**
 * 把后端返回的相对文件地址补成可直接用于 <image src> 的绝对地址。
 *
 * 已是绝对地址（http/https/协议相对）、base64 或微信本地临时文件（wxfile://、
 * http://tmp/）的原样返回：这些都能被 <image> 直接渲染，再拼主机名反而会拼坏。
 */
export function resolveFileUrl(url?: null | string): string {
  if (!url) return ''
  if (
    url.startsWith('//') ||
    url.startsWith('data:') ||
    url.startsWith('wxfile://') ||
    url.startsWith('http://tmp/') ||
    /^https?:\/\//.test(url)
  ) {
    return url
  }
  return `${API_ORIGIN}${url.startsWith('/') ? '' : '/'}${url}`
}
