# CLAUDE.md

뉴스클리핑: 신문 1면·농업·비료·국제/경제·해외 농업 헤드라인을 모아 차 블루투스 연결 시 음성으로 읽어 주는 앱 (갤럭시 APK + GitHub Pages).

**먼저 [HANDOFF.md](HANDOFF.md)를 읽으세요.** 구조, 배포, 비밀값 이름, 기능 이력, 남은 일, 주인과 일하는 규칙이 있습니다. 기능·설치 설명은 [README.md](README.md).

## 꼭 지킬 것

- 사용자와는 **한국어**로, 결과부터 쉽게 말합니다.
- 서버·웹 규칙과 앱 규칙은 쌍으로 바꿉니다: `js/brief.js` ↔ `android/.../Brief.java`, `js/pron.js` ↔ `Pron.java`. `tests/fixtures`로 맞춥니다.
- 앱을 바꾸는 PR은 `android/version.txt`를 `1.<PR 번호>`로 올립니다. 배포판이 둘입니다(정식 `full`, 제품 공부·농업영어를 뺀 라이트 `lite`). 라이트에도 해당하는 변경이면 `android/version-lite.txt`도 같은 번호로 올리고, **제품 공부·농업영어만 바꾼 PR은 `version-lite.txt`를 올리지 않습니다**(라이트에 업데이트가 뜨지 않게). 제품 공부·농업영어 기능은 `BuildConfig.LESSONS`로 감쌉니다.
- 머지 요청·머지 알림에는 항상 `PR #N (v1.N)`과 APK 링크를 씁니다:
  https://github.com/kimyh1981/newsclipping/releases/latest/download/newsclipping.apk
- "헤드라인" = 언론사 1면 주요 기사. 무료 방식 우선(유료 API 금지). 자동 재생 기본은 평일 06~08시, 공휴일 제외.
- 비밀값의 값은 절대 커밋하거나 출력하지 않습니다.
- **서버는 기사 본문을 모으거나 녹음·공개하지 않습니다**(저작권, 2026-10-18 주인 결정). 제목·언론사·원문 링크만. 본문은 폰이 '전체 듣기' 때 원문을 열어 읽고 버립니다(`ArticleReader`).

## 확인 명령

```
npm ci                      # 처음 한 번
npm test                    # 서버·웹 테스트 75개 (네트워크 없음)
npm run collect             # 실제 수집 (샌드박스에서는 뉴스 사이트 접속이 막혀 실패할 수 있음)
cd android && gradle testReleaseUnitTest assembleRelease   # Android SDK 필요 → 보통 CI(android.yml)에 맡김
```

샌드박스에는 Android SDK가 없고 네이버·구글에 접속되지 않으므로, 실제 결과는 PR의 `news-check`·`android` 워크플로 로그로 확인합니다.
