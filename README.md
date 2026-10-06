# 뉴스클리핑

매일 아침 주요 신문(연합·조선·중앙·동아·한겨레·경향), 농업 신문(농민신문·한국농어민신문·농수축산신문·농축유통신문), 비료 관련 뉴스, 국제 정세·경제, 베트남 농업·비료 뉴스(단찌 등, 우리말 번역) 헤드라인을 모아,
**평일 아침 6~8시 차 블루투스가 연결되면 자동으로 읽어 주는** 앱입니다.
한국·베트남·영문 국제·중국·일본 언론 69곳 가운데 들을 언론사를 앱과 웹에서 각자 체크해 고릅니다 (외국 언론은 제목을 우리말로 번역).
원고는 아나운서처럼 요일에 맞춘 인사와 섹션 연결 말로 읽고, 안드로이드 앱에서는 폰에 든 한국어 음성 중 가장 자연스러운 음성을 골라 씁니다 (앱의 '듣기' 묶음에서 목소리와 말 빠르기를 들어 보고 바꿀 수 있습니다).

- 설치·기사 목록: https://kimyh1981.github.io/newsclipping/
- 갤럭시 앱(APK): https://github.com/kimyh1981/newsclipping/releases/latest/download/newsclipping.apk
- 언론사별 기사: https://kimyh1981.github.io/newsclipping/briefing.json (앱과 웹이 읽는다)
- 기본 언론사 원고: https://kimyh1981.github.io/newsclipping/briefing.txt (아이폰 단축어가 읽는다, 주말·공휴일에는 빈 파일)

## 구성

| 부분 | 하는 일 |
|---|---|
| `tools/collect.js`, `feeds.json` | 매일 새벽 5시(한국 시간) RSS를 모아 `briefing.json`·`briefing.txt`를 만든다. 언론사 RSS가 막히면 구글 뉴스로 대신하고, 같은 사건은 한 번만, 제목은 소리 내어 읽기 좋게 다듬는다. `lang`이 붙은 섹션(베트남)은 베트남판 구글 뉴스에서 찾아 제목을 구글 번역으로 옮긴다 |
| `tools/holidays.js` | 주말·공휴일이면 `briefing.txt`를 비운다. 공휴일은 공공데이터포털 특일 정보(`DATA_GO_KR_KEY`), 실패하면 내장 목록 |
| `js/brief.js` | 고른 언론사로 섹션과 원고를 만드는 규칙 (서버·웹 공용, 앱의 `Brief.java`가 같은 규칙. `tests/fixtures`로 둘을 맞춘다) |
| `index.html`, `js/app.js` | 기사 목록, 듣기 버튼, 언론사 체크 목록, 설치 안내 |
| `android/` | 갤럭시 앱. 차 블루투스 연결(ACL_CONNECTED)을 받아 평일 6~8시에 하루 한 번 원고를 받아 미디어 음량으로 읽고, 연결이 끊기면 멈춘다 |
| `.github/workflows/pages.yml` | 테스트 → 원고 수집 → Pages 배포 (push, 매일 05:00·05:40) |
| `.github/workflows/news-check.yml` | PR마다 실제 출처·번역을 불러 원고를 로그로 보여 준다 |
| `.github/workflows/android.yml` | 앱 단위 테스트·APK 빌드, main이면 릴리스로 올려 설치 링크를 갱신 |

## 처음 한 번 설정 (저장소 주인)

1. Settings → Pages → Build and deployment → Source를 **GitHub Actions**로.
2. Settings → Secrets and variables → Actions → `DATA_GO_KR_KEY` (공공데이터포털 인증키, 「한국천문연구원_특일 정보」 활용신청).

## 갤럭시 앱

설치 링크를 휴대폰에서 열어 APK를 받아 설치합니다. 플레이스토어 앱이 아니라서 '출처를 알 수 없는 앱 설치'를 한 번 허용해야 합니다.
앱에서 권한(블루투스·알림), 배터리 '제한 없음'(Android 12 이상에서 차 연결 순간 백그라운드로 시작하려면 필요), 차 블루투스를 고르면 끝입니다.
차를 고르지 않으면 오디오 블루투스 기기(차 오디오·헤드셋)가 연결될 때 모두 반응합니다. 시간대와 평일만 여부는 앱에서 바꿀 수 있습니다.

APK는 `android/app/release.keystore`로 서명합니다. 같은 키로 서명해야 새 버전이 기존 앱 위에 업데이트로 설치됩니다.
저장소가 공개라 키도 공개되어 있으니, 가족·지인 밖으로 널리 배포하려면 키를 새로 만들어 저장소 비밀값으로 옮기고 `KEYSTORE_PASSWORD`를 바꾸세요.

## 아이폰 단축어 만들기 (한 번만, 아이폰에서)

아이폰은 앱이 블루투스 연결을 알아차리는 것을 허용하지 않아 단축어로 합니다. 한 번 만들어 iCloud 링크로 공유하면 다른 사람은 링크로 추가만 하면 됩니다.

1. 단축어 앱 → **+** → 이름 '뉴스클리핑'
2. **현재 날짜** → **날짜 형식 지정** (사용자 지정 `H`)
3. **만약**: 형식이 지정된 날짜가 **6과 7 사이**
4. 만약 안에: **URL** `https://kimyh1981.github.io/newsclipping/briefing.txt` → **URL의 콘텐츠 가져오기** → **텍스트 말하기** (언어 한국어, 음성은 **Siri 음성**이 가장 자연스럽습니다. 처음이면 설정 → 손쉬운 사용 → 읽기 및 말하기 → 음성 → 한국어에서 Siri 음성을 받아 둡니다)
5. 단축어 공유 → **iCloud 링크 복사**, 그 링크를 `js/app.js`의 `SHORTCUT_URL`에 넣으면 웹 화면에 '단축어 추가' 버튼이 생깁니다.

받는 사람은 링크로 단축어를 추가한 뒤 자동화 → 블루투스 → 차 → 연결될 때, 즉시 실행 → '뉴스클리핑'만 직접 연결합니다 (Apple이 자동화는 링크로 공유하지 못하게 합니다).

## 개발

```
npm test                       # 수집기·원고·공휴일·화면 테스트 (네트워크 없이)
npm run collect                # 실제 RSS를 모아 dist/에 원고 생성
cd android && gradle testReleaseUnitTest assembleRelease   # Android SDK 필요
```
