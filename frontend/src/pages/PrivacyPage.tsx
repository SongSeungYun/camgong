import { PrivacyText } from '../features/privacy/PrivacyText'
import { Layout } from '../shared/ui'
import s from './PrivacyPage.module.css'

/** 개인정보 처리방침. 로그인 없이 볼 수 있다 */
export default function PrivacyPage() {
  return (
    <Layout>
      <article className={s.policy}>
        <PrivacyText />
      </article>
    </Layout>
  )
}
