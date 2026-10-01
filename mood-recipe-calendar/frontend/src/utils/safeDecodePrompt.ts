/**
 * Decode router prompt values at the page boundary.
 * The loop is intentionally capped: it supports old one/two-times encoded
 * links without repeatedly transforming ordinary user text.
 */
export function safeDecodePrompt(value: unknown): string {
  if (typeof value !== 'string')
    return ''

  let result = value.trim()
  for (let round = 0; round < 2 && /%[0-9a-f]{2}/i.test(result); round += 1) {
    try {
      const decoded = decodeURIComponent(result.replace(/\+/g, ' '))
      if (decoded === result)
        break
      result = decoded
    }
    catch {
      break
    }
  }
  return result
}
