# F1 받아쓰기 측정 (로컬 전용)

앱의 F1 음성(F1Engine·F1Text)을 파이썬으로 그대로 옮겨, 기사 본문을 소리로 만들고 받아쓰기(faster-whisper)로
**빠진 음절 비율**과 **끝 글자 잘림**을 잰다. 2026-11-26 '말끝이 잘리고 고무줄 같다' 진단에 썼다.

- 모델: `f1model/` 아래에 허깅페이스 `supertone-oss-archive/supertonic-3` 파일 (android.yml f1-live와 같은 주소, 약 380MB)
- `node getbodies.js` → 오늘 원고(briefing.json·briefing.txt를 같은 폴더에 받아 둠)의 기사 몇 건 본문으로 `said.json` (측정용, 저장소에 넣지 않는다)
- `python f1bench.py said.json res.json app|cap` (환경변수 `F1SPEED` 빠르기, `F1RATE` 초당 음절 상한)
- `python listen.py res.json heard.json` → `node cmp.js heard.json 5`
- `hangulize.js`는 F1Text.hangulize의 자바스크립트판: 고치면 `tests/fixtures/hangulize.json` 기대값을 다시 만들고 자바도 같이 고친다

2026-11-26 결과 (본문 3건, 빠르게 130): 고치기 전 빠진 음절 6.5%·끝 잘림 3/38 → 초당 6음절 상한 + 영문 한글화 후 1.7%·0/38.
