# 서비스 Gmail 보낸편지함 정리 (Apps Script)

동의문의 "인증 메일 발송 기록에서는 7일 안에 지워집니다"를 지키는 절차다(010 plan 결정 5-1, `docs/deploy-checklist.md` 7.1의 6-1). 서비스 Gmail 계정에서 사용자가 한 번 설정한다. 이 저장소에서 실행하는 코드는 아니다. 만들어 돌려 보지는 않았다(확인 못 함).

## 왜 필요한가

- SMTP로 보낸 메일은 Gmail 보낸편지함에 자동으로 복사된다([Google 도움말](https://support.google.com/mail/answer/78892)). 인증 메일과 재설정 메일(011)에는 받는 사람의 학교 메일 주소가 들어 있다.
- 주소가 틀려 되돌아온 메일(mailer-daemon)은 받은편지함에 쌓이고, 거기에도 주소가 들어 있다.
- 휴지통으로 옮기면 30일 동안 남는다([Google 도움말](https://support.google.com/mail/answer/7401)). 그래서 휴지통을 거치지 않고 바로 지운다.

## 설정 순서

1. 서비스 Gmail로 로그인하고 [script.google.com](https://script.google.com)에서 새 프로젝트를 만든다. 이름은 `camgong-mail-cleanup`이다.
2. 왼쪽 "서비스"에서 **Gmail API**(고급 서비스, `Gmail`, v1)를 추가한다.
3. 프로젝트 설정에서 "appsscript.json 매니페스트 파일 표시"를 켜고 아래 매니페스트로 바꾼다.
4. `Code.gs`를 아래 코드로 바꾼다.
5. 함수 `purge`를 한 번 직접 실행해 권한을 허용한다. 본인만 쓰는 스크립트라도 "확인되지 않은 앱" 경고가 나올 수 있다(확인 못 함). 그때는 "고급 → 이동"을 누른다.
6. 함수 `install`을 한 번 실행한다. 매일 04시대(시각은 1시간 안에서 무작위)에 `purge`가 돈다.
7. 실행 기록에서 `purge deleted=N`이 남는지 본다. 트리거가 실패하면 Google이 계정 주소로 "Summary of failures for Apps Script" 메일을 보낸다.

### appsscript.json

```json
{
  "timeZone": "Asia/Seoul",
  "exceptionLogging": "STACKDRIVER",
  "runtimeVersion": "V8",
  "oauthScopes": ["https://mail.google.com/"],
  "dependencies": {
    "enabledAdvancedServices": [{ "userSymbol": "Gmail", "serviceId": "gmail", "version": "v1" }]
  }
}
```

`https://mail.google.com/`는 영구 삭제(`users.messages.delete`, `batchDelete`)에 필요한 범위다([delete](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/delete), [batchDelete](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/batchDelete)).

### Code.gs

```js
// 6일(초 단위)이 지난 보낸 메일과 반송 메일을 휴지통을 거치지 않고 지운다. 하루 한 번 돌면 어떤 메일도 7일을 넘기지 않는다.
// 남겨 둘 메일은 'keep' 라벨을 붙이면 지우지 않는다(라벨은 Gmail에서 미리 만든다).
const DAYS = 6;
const QUERY = '{in:sent from:mailer-daemon} -label:keep';

function purge() {
  const cutoff = Math.floor(Date.now() / 1000) - DAYS * 86400; // older_than:은 일 단위라 경계가 흐려서 초 단위 before:를 쓴다
  const q = `${QUERY} before:${cutoff}`;
  let deleted = 0;
  // 지우는 동안 페이지 토큰을 따라가면 건너뛸 수 있어 매번 처음부터 다시 찾는다. 지워지지 않는데 계속 찾는 일을 막으려고 횟수를 묶는다.
  for (let round = 0; round < 50; round++) {
    const ids = (Gmail.Users.Messages.list('me', { q, maxResults: 500 }).messages || []).map((m) => m.id);
    if (ids.length === 0) break;
    Gmail.Users.Messages.batchDelete({ ids }, 'me');
    deleted += ids.length;
  }
  console.log(`purge deleted=${deleted}`);
}

function install() {
  ScriptApp.getProjectTriggers().forEach((t) => ScriptApp.deleteTrigger(t));
  ScriptApp.newTrigger('purge').timeBased().everyDays(1).atHour(4).create();
}
```

- 한 번에 500개씩, 최대 50번(25,000통) 지운다. 그보다 많이 쌓이면 다음 날 이어서 지운다.
- 쿼터: list 5단위, batchDelete 50단위, 사용자당 분당 6,000단위([쿼터](https://developers.google.com/workspace/gmail/api/reference/quota)). 위 반복은 최대 2,750단위다. 개인 계정 Apps Script는 실행당 6분, 트리거 실행 합계는 하루 90분이다([한도](https://developers.google.com/apps-script/guides/services/quotas)).
- `batchDelete`의 한 번당 개수 상한은 문서에 없다. 비슷한 `batchModify`가 1,000개라 500개로 나눴다.

## 받은편지함의 문의 메일

처리방침의 보호책임자 연락처로 온 문의(열람·정정·삭제 요청 등)는 받은편지함에 남는다. 위 스크립트는 받은편지함의 일반 메일을 지우지 않는다. 처리가 끝난 문의를 언제 지울지는 개인정보 처리방침에 적는다(`docs/privacy-policy-v1.md`).

## 확인 못 한 것

- 실제 계정에서 돌려 보지 않았다. 권한 화면, 실행 시간, 트리거 실패 알림의 빈도 선택지.
- Gmail 읽기·쓰기 하루 한도(20,000건)에 고급 서비스 호출이 들어가는지.
- 보낸 메일을 보낸편지함에 남기지 않게 끄는 설정이 있는지.
