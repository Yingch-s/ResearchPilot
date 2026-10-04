// Markdown 渲染（marked + DOMPurify），LLM 产出的报告/回答按 md 展示
import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({ breaks: true, gfm: true })

export function md(text) {
  if (!text) return ''
  const parsed = marked.parse(String(text))
  const doc = new DOMParser().parseFromString(parsed, 'text/html')
  const headings = [...doc.body.querySelectorAll('h1, h2, h3, h4')]
  const heading = headings.find((el) => /^(参考文献|引用来源|References|Sources)$/i.test(el.textContent.trim()))
  if (heading) {
    const details = doc.createElement('details')
    details.className = 'model-references'
    const summary = doc.createElement('summary')
    summary.textContent = heading.textContent.trim()
    details.append(summary)
    let sibling = heading.nextSibling
    while (sibling) {
      const next = sibling.nextSibling
      if (sibling.nodeType === 1 && /^H[1-4]$/.test(sibling.tagName)) break
      details.append(sibling)
      sibling = next
    }
    heading.replaceWith(details)
  }
  return DOMPurify.sanitize(doc.body.innerHTML)
}
