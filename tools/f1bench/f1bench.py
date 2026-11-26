# 앱의 F1Engine·F1Text를 그대로 옮긴 측정용 (로컬 전용, 저장소 밖 _f1bench).
# 덩이마다 예측 길이·음절 수·초당 음절을 재고, 소리를 만들어 받아쓰기로 끝 글자·낱말 빠짐을 본다.
# 사용: python f1bench.py <said.json> <out.json> [mode]
#   said.json: [{"id":..., "said": Pron.say 결과}], mode: app(지금 앱) | fixed(고정 빠르기 후보)
import json, sys, math, os, re, unicodedata, random
import numpy as np
import onnxruntime as ort

D = os.path.join(os.path.dirname(__file__), 'f1model')
cfg = json.load(open(os.path.join(D, 'onnx/tts.json'), encoding='utf-8'))
SR = cfg['ae']['sample_rate']; BASE = cfg['ae']['base_chunk_size']
COMP = cfg['ttl']['chunk_compress_factor']; LAT = cfg['ttl']['latent_dim']
o = ort.SessionOptions(); o.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
S = {n: ort.InferenceSession(os.path.join(D, 'onnx', n + '.onnx'), o, providers=['CPUExecutionProvider'])
     for n in ['duration_predictor', 'text_encoder', 'vector_estimator', 'vocoder']}
IDX = json.load(open(os.path.join(D, 'onnx/unicode_indexer.json')))
st = json.load(open(os.path.join(D, 'voice_styles/F1.json')))
def style(s): return np.array(s['data'], dtype=np.float32).reshape(s['dims'])
TTL, DP = style(st['style_ttl']), style(st['style_dp'])
STEPS = 8
rng = np.random.default_rng(7)

# ---- F1Text (자바와 같게) ----
NUMBER_UNIT = re.compile(r'(?<=[일이삼사오육칠팔구])([천백])(?=[일이삼사오육칠팔구십백])|(?<=천)(백)(?=[일이삼사오육칠팔구십])')
TEN_UNIT = re.compile(r'(?<=[이삼사오육칠팔구])십(?=[일이삼사오육칠팔구][억만조])')
def space_numbers(t):
    t = NUMBER_UNIT.sub(lambda m: (m.group(1) or m.group(2)) + ' ', t)
    return TEN_UNIT.sub('십 ', t)
SYMBOLS = [("–", "-"), ("‑", "-"), ("—", "-"), ("_", " "), ("“", '"'), ("”", '"'), ("‘", "'"), ("’", "'"), ("´", "'"), ("`", "'"),
           ("[", " "), ("]", " "), ("|", " "), ("/", " "), ("#", " "), ("→", " "), ("←", " "), ("@", " at ")]
ENDS = re.compile(r'.*[.!?;:,\'"“”‘’)\]}…。」』】〉》›»]$', re.S)
def prepare(text, lang='ko'):
    if lang == 'ko': text = space_numbers(text)
    text = unicodedata.normalize('NFKD', text)
    for a, b in SYMBOLS: text = text.replace(a, b)
    text = re.sub(r'[♥☆♡©\\]', '', text)
    for p in [',', '.', '!', '?', ';', ':', "'"]: text = text.replace(' ' + p, p)
    text = re.sub(r'\s+', ' ', text).strip()
    if not ENDS.match(text): text += '.'
    return f'<{lang}>{text}</{lang}>'
MIN_PART = 25
def cut_long(s, mx, out):
    while len(s) > mx:
        n = (len(s) + mx - 1) // mx; target = len(s) / n; best = -1; bd = 1e9
        i = s.find(', ')
        while i >= 0:
            L = i + 1
            if L <= mx and L >= MIN_PART and len(s) - L - 1 >= MIN_PART and abs(L - target) < bd: bd = abs(L - target); best = L
            i = s.find(', ', i + 1)
        if best < 0:
            i = s.find(' ')
            while i >= 0:
                if i <= mx and i >= MIN_PART and len(s) - i - 1 >= MIN_PART and abs(i - target) < bd: bd = abs(i - target); best = i
                i = s.find(' ', i + 1)
        if best < 0:
            best = s.rfind(' ', 0, mx + 1)
            if best <= 0: best = mx
        left = s[:best].strip()
        if not re.match(r'(?s).*[,.!?]$', left): left += ','
        out.append(left); s = re.sub(r'^[,\s]+', '', s[best:])
    if s: out.append(s)
def chunks(text, mx=80):
    text = text.strip(); out = []
    for para in re.split(r'\n\s*\n', text):
        para = para.strip()
        if not para: continue
        if len(para) <= mx: out.append(para); continue
        cur = ''
        for s in re.split(r'(?<=[.!?])\s+', para):
            s = s.strip()
            if not s: continue
            if len(s) > mx:
                if cur: out.append(cur.strip()); cur = ''
                cut_long(s, mx, out); continue
            if len(cur) + len(s) + 1 > mx and cur: out.append(cur.strip()); cur = ''
            cur = (cur + ' ' if cur else '') + s
        if cur: out.append(cur.strip())
    return out or ['']

def ids_of(chunk):
    cps = [ord(c) for c in prepare(chunk)]
    return np.array([[IDX[c] if c < len(IDX) else 0 for c in cps]], dtype=np.int64), len(cps)

def predict(chunk):
    ids, n = ids_of(chunk); mask = np.ones((1, 1, n), np.float32)
    return float(S['duration_predictor'].run(None, {'text_ids': ids, 'style_dp': DP, 'text_mask': mask})[0].reshape(-1)[0])

def infer(chunk, duration, cut=True):
    ids, n = ids_of(chunk); mask = np.ones((1, 1, n), np.float32)
    emb = S['text_encoder'].run(None, {'text_ids': ids, 'style_ttl': TTL, 'text_mask': mask})[0]
    wav_len = int(duration * SR); cs = BASE * COMP
    ll = (wav_len + cs - 1) // cs; dim = LAT * COMP
    xt = rng.standard_normal((1, dim, ll)).astype(np.float32)
    lm = np.ones((1, 1, ll), np.float32); tot = np.array([STEPS], np.float32)
    for step in range(STEPS):
        xt = S['vector_estimator'].run(None, {'noisy_latent': xt, 'text_emb': emb, 'style_ttl': TTL, 'latent_mask': lm,
                                              'text_mask': mask, 'current_step': np.array([step], np.float32), 'total_step': tot})[0]
    wav = S['vocoder'].run(None, {'latent': xt})[0][0]
    return wav[:min(len(wav), wav_len)] if cut else wav

def syl(s):  # 읽는 음절 수: 한글 음절 + 영문 글자·숫자는 대략 1
    return len(re.findall(r'[가-힣]', s)) + len(re.findall(r'[A-Za-z0-9]', s))

if __name__ == '__main__':
    items = json.load(open(sys.argv[1], encoding='utf-8'))
    mode = sys.argv[3] if len(sys.argv) > 3 else 'app'
    speed = float(os.environ.get('F1SPEED', '1.05'))
    rate = float(os.environ.get('F1RATE', '6.2'))  # fixed 모드의 기준 음절/초 (빠르기 1.0일 때)
    res = []
    os.makedirs('wav', exist_ok=True)
    for it in items:
        parts = []
        for k, ch in enumerate(chunks(it['said'])):
            pred = predict(ch)
            if mode == 'app':
                dur = pred / speed
            elif mode == 'cap':  # 초당 음절 상한: 빠르기는 그대로 따르되 rate(음절/초)를 넘지 않게
                dur = max(pred / speed, syl(ch) / rate)
            else:  # 고정 빠르기: 음절 수로 정한 길이와 예측 길이 중 긴 쪽 (덜 몰아 읽게)
                dur = max(pred, syl(ch) / rate) / speed
            w = infer(ch, dur, cut=(mode in ('app', 'cap')))
            fn = f"wav/{it['id']}_{k}_{mode}.wav"
            import wave
            pcm = np.clip(w, -1, 1)
            with wave.open(fn, 'wb') as f:
                f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR)
                f.writeframes((pcm * 32767).astype(np.int16).tobytes())
            parts.append({'chunk': ch, 'syl': syl(ch), 'pred': round(pred, 3), 'dur': round(dur, 3),
                          'sps': round(syl(ch) / dur, 2), 'pred_sps': round(syl(ch) / pred * speed, 2), 'wav': fn})
        res.append({'id': it['id'], 'parts': parts})
        print(it['id'], [(p['syl'], p['sps']) for p in parts], flush=True)
    json.dump(res, open(sys.argv[2], 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
