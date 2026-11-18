// docs/consent-v1.md 두 구분선 사이 본문 그대로(v1). 문구를 바꾸면 VerifyPage의 CONSENT_VERSION을 올린다.
// 본문과 같은지는 src/pages/Consent.test.tsx가 문서를 읽어 확인한다.
const ROWS: [string, string][] = [
  ['학교 이메일', '재학생 확인, 로그인'],
  ['표시 이름', '공구에서 나를 알아보게 하기'],
  ['비밀번호(복원할 수 없게 암호화해 저장)', '로그인'],
  ['공구 개설·참여 기록(수량, 입금·수령 확인 여부)', '공동구매 진행과 기록'],
]

export function ConsentText() {
  return (
    <>
      <p><strong>캠공 개인정보 수집·이용 동의</strong></p>
      <p>캠공은 같은 학교 학생끼리 공동구매를 하기 위해 아래 정보를 받습니다.</p>
      <table aria-label="받는 정보와 목적">
        <thead>
          <tr><th scope="col">항목</th><th scope="col">목적</th></tr>
        </thead>
        <tbody>
          {ROWS.map(([item, purpose]) => (
            <tr key={item}><td>{item}</td><td>{purpose}</td></tr>
          ))}
        </tbody>
      </table>
      <ul>
        <li><strong>다른 사용자에게 보이는 정보</strong>: 내 표시 이름은 공구에 함께 보입니다. 내가 참여한 공구의 개설자는 내 수량과 입금·수령 확인 여부도 봅니다. 이메일은 다른 사용자에게 보이지 않습니다.</li>
        <li><strong>맡기는 곳</strong>: 인증 메일은 Google(Gmail)로 보내고, 서버는 Amazon Web Services(서울 리전)에서 운영합니다.</li>
        <li><strong>보관 기간</strong>: 탈퇴할 때까지 보관합니다. 탈퇴는 내 활동 화면에서 바로 할 수 있고, 탈퇴하면 이메일·표시 이름·비밀번호를 즉시 지웁니다. 함께한 공구의 수량과 입금·수령 기록은 다른 참여자의 기록을 위해 "탈퇴한 회원"으로 남습니다. 알림은 30일이 지나면 자동으로 지웁니다. 문의: <code>[운영자 연락처]</code></li>
        <li><strong>돈은 다루지 않습니다</strong>: 결제는 서비스 밖에서 합니다. 캠공은 계좌·결제 정보를 받지 않습니다.</li>
        <li><strong>동의하지 않을 수 있습니다</strong>: 다만 동의하지 않으면 가입할 수 없습니다.</li>
      </ul>
    </>
  )
}
