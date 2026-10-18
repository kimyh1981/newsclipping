# 뉴스클리핑 인수인계 (2026-10-16 기준)

다른 Claude 계정(또는 다른 개발자)이 이 저장소에서 바로 이어서 개발할 수 있도록 정리한 문서입니다.
기능 설명·설치·설정 방법은 [README.md](README.md)에 자세히 있고, 이 문서는 **개발을 이어받는 데 필요한 것**만 모았습니다.

- 현재 버전: **v1.73** (정식 `android/version.txt`, 라이트 `android/version-lite.txt`)
- 웹: https://kimyh1981.github.io/newsclipping/
- APK: https://github.com/kimyh1981/newsclipping/releases/latest/download/newsclipping.apk
- 라이트 APK(제품 공부·농업영어 없음, '뉴스클리핑 라이트'): https://github.com/kimyh1981/newsclipping/releases/latest/download/newsclipping-lite.apk
- 정식판 받기 페이지(바이오플랜 직원에게 나눠 줄 주소): https://kimyh1981.github.io/newsclipping/app/ (`app/index.html`, 라이트 페이지와 같은 구조·색)
- 라이트 받기 페이지(사람들에게 나눠 줄 주소, 설치 안내 포함): https://kimyh1981.github.io/newsclipping/lite/ (`lite/index.html`)
- 주인: GitHub `kimyh1981` (바이오플랜). 머지는 주인이 직접 합니다.

## 1. 새 계정에서 처음 할 일

1. **GitHub 접근**: 새 Claude 계정이 쓰는 GitHub 계정이 이 저장소에 쓰기 권한이 있어야 합니다. 같은 `kimyh1981` 계정을 claude.ai에 연결하면 그대로 됩니다(https://claude.ai/connect-github, 저장소에 Claude GitHub 앱 설치).
2. **저장소 연결**: Claude Code(웹/앱) 환경에 `kimyh1981/newsclipping`을 추가합니다. 저장소 루트의 `CLAUDE.md`를 Claude가 자동으로 읽습니다.
3. **예약 작업(루틴) 다시 만들기**: 아래 두 루틴은 이전 Claude 계정에 묶여 있어 새 계정으로 따라오지 않습니다. 새 계정에서 같은 일을 하는 루틴을 만들고, 옛 계정 루틴은 끄세요(둘 다 켜져 있어도 15분 안 중복 실행은 건너뛰게 되어 있어 큰 문제는 없습니다).

| 이름 | 일정 | 하는 일 |
|---|---|---|
| 뉴스클리핑 새벽 원고 깨우기 | `CRON_TZ=Asia/Seoul 27 4 * * 1-5` | main의 `pages.yml`("Deploy news site")을 workflow_dispatch로 실행. 15분 안에 시작된 pages 실행이 있으면 건너뜀 |
| 뉴스클리핑 오후 원고 깨우기 | `CRON_TZ=Asia/Seoul 27 16 * * 1-5` | 같음 (오후판) |

   GitHub의 예약 실행(cron)이 몇 시간씩 늦는 일이 있어서 두는 보조 장치입니다. `alarm.yml`도 같은 목적으로 전날 밤에 깨어 기다립니다.

4. **프로젝트 공유 파일**: 이전 프로젝트에만 있던 문서를 [`docs/handoff/`](docs/handoff/)로 옮겨 두었습니다(농업영어 코너 컨셉, 언론사 후보 목록). 음성 샘플 mp3는 옮기지 않았습니다.

## 2. 구조 한눈에

```
GitHub Actions (새벽·오후)                     폰/웹
 tools/collect.js ── feeds.json                 android/ (Java, 갤럭시 앱)
   ├ naver.js   : 신문 1면 / 주요뉴스 / 많이본뉴스   CarReceiver → NewsService(TTS)
   ├ rss.js     : RSS, 구글뉴스 대체               Brief.java  (= js/brief.js)
   ├ summarize.js: 본문 요약(기본 리드)             Pron.java   (= js/pron.js)
   ├ holidays.js: 주말·공휴일 → briefing.txt 비움    Updater.java (앱 내 업데이트)
   ├ tts.js + edge_tts_batch.py : audio/ 녹음      index.html + js/app.js (웹)
   └ dist/ → GitHub Pages (briefing.json, briefing.txt, audio/, archive/, ios/)
```

- **서버·웹·앱이 같은 규칙을 두 번 구현**합니다. `js/brief.js` ↔ `Brief.java`, `js/pron.js` ↔ `Pron.java`. 한쪽을 바꾸면 다른 쪽도 바꾸고 `tests/fixtures`(script-*.txt, pron.json)로 둘을 맞춥니다.
- 앱은 서버가 만든 `briefing.json`을 받아 사용자가 고른 언론사로 원고를 만들고, 녹음된 줄은 `audio/` 파일로, 나머지는 폰 TTS로 읽습니다.
- 해외 키워드 뉴스(`Abroad.java`, `Countries.java`)는 앱에서 직접 구글 뉴스 검색 + 무료 구글 번역(gtx)으로 만듭니다.
- 제품 공부·농업영어: **잠겨 있습니다**(퇴사자가 못 보게, 2026-10-17 A안). 저장소에는 `lessons/lessons.enc.json`만 있고, 원문(`product.json`, `english.json`, `make_english.py`, 음성 en-US-AndrewNeural -10%)은 주인 PC `H:\Newscliping\_lessons\`(git 제외)에 있습니다. 고치는 법: `_lessons`의 원문을 고침 → `node tools/lessons-crypt.js seal --code-file _keys/lesson-code.txt` → `lessons.enc.json` 커밋. 서버는 비밀값 `LESSON_CODE`로 풀어 녹음만 하고, 공개 원고에는 `lessonsEnc`(잠긴 채)로 넣습니다. 앱(정식판)은 제품공부를 처음 열 때 사내 코드를 물어 `LessonLock`으로 풉니다. **퇴사자가 나오면** 새 코드로 seal → `LESSON_CODE` 교체 → 직원들에게 새 코드 공지. 진행도는 폰에 저장(`Lessons.java`, `Prefs`). 평일 오전에만 붙습니다.

## 3. 워크플로·배포

| 파일 | 내용 |
|---|---|
| `pages.yml` | 테스트 → 수집 → 녹음 → Pages 배포. 새벽 cron(04:30~06:00 KST)·오후 cron(16:20~17:20 KST), push, dispatch. `concurrency: pages`, cancel-in-progress, 30분 타임아웃 |
| `alarm.yml` | 전날 밤 깨어 기다렸다가 04:40·05:50 KST에 pages.yml을 dispatch |
| `evening-alarm.yml` | 평일 낮에 깨어 기다렸다가 17:00·17:35 KST에 pages.yml을 dispatch (오후판 = 그날 많이 본 기사, 퇴근길 18~20시) |
| `android.yml` | 정식·라이트 앱 단위 테스트·APK 빌드. main이면 릴리스 `v<version.txt>-r<run번호>`에 `newsclipping.apk`와 `newsclipping-lite.apk`(표시 이름 = `v<version-lite.txt>`)를 올림 |
| `news-check.yml` | PR마다 실제 출처를 불러 원고를 로그로 보여 줌 (샌드박스에서 뉴스 사이트에 못 들어가므로 여기서 실제 결과 확인) |

- **배포판 두 가지** (주인 요청 2026-10-17): `build.gradle`의 productFlavors. `full` = 지금 앱 그대로, `lite` = '뉴스클리핑 라이트'(앱 ID `io.github.kimyh1981.newsclipping.lite`라 정식판과 따로 설치). 라이트는 `BuildConfig.LESSONS=false`로 제품 공부·농업영어 화면·탭·재생을 뺍니다. 공통 수정은 같은 코드라 두 앱에 똑같이 들어갑니다.
- **앱 버전 규칙**: 앱을 바꾸는 PR마다 `android/version.txt`를 `1.<PR 번호>`로 올립니다. 라이트에도 해당하면 `android/version-lite.txt`도 같은 번호로 올립니다. **제품 공부·농업영어만 바꾼 PR은 `version-lite.txt`를 그대로 둡니다**: 라이트 앱은 릴리스의 라이트 APK 표시 이름(라이트 버전)이 자기 버전과 다를 때만 업데이트를 띄웁니다(`Updater.isNewer`). `versionCode`는 Actions run 번호라서 자동으로 증가합니다. 서버만 바꾸는 PR은 올리지 않아도 됩니다.
- **서명 키**: `android/app/release.keystore` (저장소에 공개, 기본 비밀번호는 build.gradle 참고). 같은 키여야 기존 앱 위에 업데이트됩니다. 바꾸면 사용자가 앱을 지우고 다시 깔아야 합니다.
- **앱 내 업데이트**: `Updater.java`가 releases/latest 태그의 버전과 설치된 versionCode를 비교해 PackageInstaller로 설치합니다.

### 비밀값·변수 (이름만, 값은 저장소 Settings에 이미 있음)

| 종류 | 이름 | 용도 |
|---|---|---|
| Secret | `DATA_GO_KR_KEY` | 공공데이터포털 특일 정보(공휴일). 2028-10-06까지 유효 |
| Secret | `GOOGLE_TTS_KEY` | (선택) 구글 클라우드 TTS. 없으면 엣지 TTS |
| Secret | `ANTHROPIC_API_KEY` | (선택) `SUMMARY_MODE=claude`일 때만. 기본은 안 씀(유료) |
| Variable | `TTS_VOICE` | 서버 음성. 기본 `ko-KR-HyunsuMultilingualNeural`, 실패 시 `ko-KR-InJoonNeural` |
| Variable | `TTS_ENGINE` | `off`면 녹음 안 함 |
| Variable | `SUMMARY_MODE` | 기본 `lead`(본문 첫 문장) |
| (선택) | `KEYSTORE_PASSWORD` | 서명 키 비밀번호를 바꿀 때 |
| Secret | `LITE_KEYSTORE_B64`, `LITE_KEYSTORE_PASSWORD` | 라이트판 비공개 서명 키(PKCS12, 별칭 `lite`). 없으면 main 배포가 멈춤. **원본은 주인 PC `H:\Newscliping\_keys\`(git 제외). 잃어버리면 라이트 사용자가 앱을 지우고 다시 깔아야 함** |
| Secret | `LESSON_CODE` | 사내 코드: 잠긴 제품 공부(`lessons/lessons.enc.json`)를 서버가 녹음하려고 풀 때 씀. 직원은 앱에 같은 코드를 넣음. **원본은 주인 PC `H:\Newscliping\_keys\lesson-code.txt`** |

Pages Source는 **GitHub Actions**로 설정되어 있어야 합니다.

## 4. 개발 환경 제약 (Claude 클라우드 샌드박스)

- 샌드박스에서는 **네이버·뉴스 사이트·구글에 접속이 안 됩니다**. 실제 수집 결과는 PR의 `news-check` 로그와 Actions 로그로 확인합니다.
- **Android SDK가 없어** 로컬에서 APK를 못 만듭니다. `android.yml`이 빌드·테스트하며, 순수 자바 로직은 `android/app/src/test`의 단위 테스트로 확인합니다.
- 서버 테스트: 처음에 `npm ci`, 그다음 `npm test` (네트워크 없이 동작, 71개). `npm ci`를 빼먹으면 4개가 실패합니다.
- 실제 갤럭시·차량 테스트는 주인이 합니다. 변경 후 "실제 폰에서 아직 시험하지 못함"을 솔직하게 알립니다.

## 5. 기능 이력 (요약)

| PR | 버전 | 내용 |
|---|---|---|
| #1 | 1.0.2 | Personal-Project에서 이 저장소로 이전 (Pages 경로 충돌 때문) |
| #14 | | '헤드라인' = 각 신문의 그날 **1면** 기사 (`tools/naver.js`) |
| #20 | | `alarm.yml`, 스테레오 녹음(ffmpeg, 고음 낮춤), 아이폰 `?car` 화면 |
| #27 | | 멈춘 pages 실행이 새벽 실행을 막던 문제 → cancel-in-progress + 30분 타임아웃 |
| #28 | | 언론사별 고르게 선택, 스팸 거르기, 농업·비료 `demote` |
| #30·#31 | | 숫자 읽기(`pron.js`=`Pron.java`), KBS 아나운서 투(사용자 선호: 한석준 아나운서 스타일), 모든 본문 녹음 |
| #32·#33 | | edge-tts 7.3.1로 403 해결, 기본 음성 인준 |
| #36 | 1.36 | 평일 제품 공부(20회) + 같은 회차 농업영어 |
| #37 | 1.37 | 앱 내 업데이트, 설정 화면 스타일 팝업(`Sheet.java`), 자막 화면 넘기기 |
| #38~#41 | 1.41 | 홈 = 재생 버튼 + 고른 언론사 로고 벽, 탭 5개, 신문 아이콘 |
| #42~#46 | 1.46 | 해외 비료·농자재 시장 뉴스(바이오플랜 해외 영업용), 설치 막힘 안내, 국내/해외 농업 키워드 분리(GDP 상위 100개국, 대륙별) |
| #47 | 1.47 | 서버 음성 현수 다국어(영어는 미국식), 나라별 화폐 읽기 |
| #48 | 1.48 | 하루 두 번(오후판: 네이버 많이 본 뉴스, 저녁 인사, 공부는 오전만), 본문 재시도·다른 언론사 대체 |
| #49 | 1.49 | 해외 키워드 뉴스 나라 간 중복 제거·현지 기사 우선, 해외 기사는 나라 이름으로 시작, 제미나이 닫히면 자동 이어 읽기 |
| #51 | | 영농자재신문·한국무역신문 추가, 같은 제목 3번 이상(사이트 화면 제목) 거르기 |
| #53 | 1.53 | F1 음성: 폰에서 실시간으로 만들어 읽기(Supertonic-3, 약 400MB 받기, 기본 꺼짐) |
| #54 | 1.54 | 앱이 꺼진 이유 보여 주기, F1을 쓰다 꺼지면 F1 자동 끄기 |
| #55 | | 영농자재신문·한국무역신문을 비료 섹션으로(비료 기사만), RSS 시간 초과 시 재시도 |
| #56 | 1.56 | 배포판 두 가지: 정식 + 뉴스클리핑 라이트(제품 공부·농업영어 없음) |
| #57 | 1.57 | F1 엔진 ONNX Runtime 1.23.1 → 1.30.0: 최신 스냅드래곤에서 모델을 열다 SIGILL로 앱이 꺼지던 문제 |
| #58 | 라이트 1.58 | 라이트판만 비공개 서명 키. 정식판 키·버전은 그대로 |
| #59 | | 비료 섹션: 영농자재신문·한국무역신문 먼저(각 1건), 시간대 없는 RSS 날짜는 한국 시간 |
| #60 | 1.60 | 말 빠르기: F1을 쓰면 빠르기 미리 듣기도 F1으로, 듣는 중 바꾸면 다음 줄부터 새 빠르기. f1-live가 빠르기별 길이를 잰다 |
| #62 | 1.62 | F1: 우리말 숫자를 천·백 단위마다 띄워 넘김(삼천 사백 오십육억). 긴 숫자에서 한 글자 건너뛰던 문제 |
| #63 | 1.63 | 홈 화면 진행바: 섹션마다 끊어 그림, 지금 읽는 언론사 로고가 커짐, 끌면 그 언론사 로고가 커지고 놓으면 거기부터 읽음 (`Chapters`, `ChapterBar`, `LogoWall.setFocus`) |
| #64 | 1.64 | 홈 화면 당겨서 새로 받기(놓으면 한 번 묻고 받음): 맨 위에 원고 시각, 받는 중 %. 서버의 최신 briefing.json을 다시 받음(읽는 중인 원고는 그대로) |
| #65 | 1.65 | 홈 화면: 듣기 카드(단추·진행바·지난 시간/전체 시간)와 듣는 언론사 카드로 나눔, 커진 로고를 칸의 2.4배·최소 140dp로 |
| #66 | 1.66 | 당겨서 새로 받기: 먼저 `latest.json`(원고 시각만)과 서버 원고 만들기 실행 상태(공개 API)를 확인 → 새 원고 있음/서버가 모으는 중/이미 최신을 나눠 안내 |
| #67 | 1.67 | 퇴근길 자동 재생(평일 18~20시, 오후판=많이 본 기사, 설정 스위치 기본 켬), 오후 알람 17:00·17:35, F1이 2.5초 넘게 늦으면 "기사를 불러오고 있습니다" 안내 |
| #68 | 라이트 1.68 | 라이트판을 정식판과 같은 최신 기능으로 올림, 라이트 받기 페이지 `lite/` (설치 방법·막힐 때·기능 소개, 최신 버전 표시) |
| #69 | | 배포 멈춤 고침: 사이트 파일 테스트에 lite/ 반영 |
| #70 | | 정식판 받기 페이지 `app/` (직원용, 제품 공부·농업영어·F1 소개) |
| #71 | 1.71 | 새로고침: 당기면 먼저 묻고(팝업에 지금 원고 시각) → 받기 누르면 확인 후 새 원고면 받음. 맨 위 원고 시각 줄은 평소 숨김. 홈의 '듣는 언론사' 글귀 뺌, 로고가 넘치면 위부터 채워 아래에서 잘림 |
| #72 | 1.72 | 선명한 로고: 서버가 언론사마다 가장 큰 아이콘을 골라 `logos/`에 올림(`tools/logos.py`, 7일 캐시), 앱은 그것을 먼저 받음(폰 캐시 logos2) |
| #73 | 1.73 | 제품 공부·농업영어 잠금(A안): 저장소·공개 원고에는 암호문만, 앱은 사내 코드로 풂(`LessonLock`, `tools/lessons-crypt.js`) |

자세한 내용은 각 PR 설명과 커밋 메시지를 보세요.

## 6. 남은 일 / 확인할 것

- **v1.49 실기기 확인 대기**: 제미나이 닫은 뒤 자동 이어 읽기, 해외 키워드 중복 제거. 주인 피드백을 기다리는 중.
- **오후판 많이 본 뉴스 페이지 구조**: 샌드박스에서 확인 못 함. 순위를 못 읽으면 1면으로 대신하고 수집 기록에 남기게 되어 있으니 Actions 로그 확인.
- 엣지 TTS는 비공식이라 언제든 막힐 수 있음. 막히면 edge-tts 버전부터 확인(10월 403은 7.3.1로 해결).
- 새벽에 원고가 없으면: 먼저 'waiting'에 걸린 pages 실행이 없는지 확인.
- 아이폰은 블루투스 감지 불가 → 단축어/`?car` 화면 방식 유지.

## 7. 주인과 일하는 규칙

- **소통은 한국어**, 쉬운 말로. 결과부터 말하기.
- **"헤드라인"** = 각 언론사 **1면 주요 기사** (최신순·순위 아님).
- 자동 재생: **평일 06~08시(KST), 공휴일 제외**가 기본.
- **무료 우선**. 유료 API는 쓰지 않음(음성은 무료 MS 엣지 TTS).
- 주인 폰은 **갤럭시**. 아이폰도 염두(단축어).
- 웹에서 **최근 7일 원고**를 날짜로 골라 볼 수 있게 유지.
- **머지 요청·머지 알림마다** PR 번호와 앱 버전을 괄호로 함께 쓰고, APK 링크를 붙입니다. 예:
  > PR #50 (v1.50) 머지 부탁드립니다.
  > APK: https://github.com/kimyh1981/newsclipping/releases/latest/download/newsclipping.apk
  > 설치한 뒤 설정 탭 맨 아래가 "버전 1.50"인지 확인해 주세요.

  서버만 바뀐 PR이면 "앱은 그대로"라고 알립니다.
- PR은 Claude가 만들고, 빌드(android, news-check, pages 테스트)가 통과한 뒤 머지를 요청합니다. 머지는 주인이 합니다.
