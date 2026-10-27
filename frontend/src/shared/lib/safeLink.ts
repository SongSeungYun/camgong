/** `https:` 주소만 링크로 허용한다. 그 외(http, javascript: 등)와 잘못된 값은 null. */
export function safeHttpsUrl(raw: string | null | undefined): string | null {
  if (!raw) return null
  try {
    const u = new URL(raw)
    return u.protocol === 'https:' ? u.href : null
  } catch {
    return null
  }
}

/** 외부 링크 `<a>`에 펼쳐 쓰는 속성. */
export const externalLinkProps = { target: '_blank', rel: 'noopener noreferrer' } as const
