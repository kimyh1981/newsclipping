# 만든 소리를 받아쓰기해서 원래 글과 맞춰 본다: 끝 글자가 빠졌는지, 낱말이 빠졌는지
# 사용: python listen.py res.json out.json
import json, sys, re, difflib
from faster_whisper import WhisperModel
m = WhisperModel('medium', device='cpu', compute_type='int8')
res = json.load(open(sys.argv[1], encoding='utf-8'))
H = lambda s: re.sub(r'[^가-힣0-9A-Za-z]', '', s)
out = []; total = cut_end = 0; miss_words = []
for it in res:
    for p in it['parts']:
        segs, _ = m.transcribe(p['wav'], language='ko', beam_size=5, vad_filter=False, condition_on_previous_text=False)
        heard = ' '.join(s.text.strip() for s in segs)
        a, b = H(p['chunk']), H(heard)
        ratio = difflib.SequenceMatcher(None, a, b).ratio()
        # 끝 두 글자가 받아쓰기 끝에 있나
        end_ok = b.endswith(a[-2:]) or b.endswith(a[-1:]) if a else True
        # 빠진 낱말: 원문 낱말 중 받아쓰기에 없는 것 (2글자 이상)
        lost = [w for w in re.findall(r'[가-힣A-Za-z]{2,}', p['chunk']) if H(w) not in b and difflib.SequenceMatcher(None, H(w), b).find_longest_match(0, len(H(w)), 0, len(b)).size < len(H(w)) - 1]
        total += 1; cut_end += (not end_ok)
        miss_words += lost
        out.append({'id': it['id'], 'chunk': p['chunk'], 'heard': heard, 'ratio': round(ratio, 3), 'end_ok': end_ok, 'lost': lost, 'sps': p['sps']})
        print(f"{it['id']} r={ratio:.2f} end={'ok' if end_ok else 'CUT'} lost={lost[:4]}", flush=True)
json.dump(out, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('chunks', total, 'end cut', cut_end, 'lost words', len(miss_words))
