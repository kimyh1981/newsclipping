#!/usr/bin/env python3
"""농업영어 원고(lessons/english.json)를 만든다. 원고를 고치려면 이 파일을 고치고 다시 실행한다:
    python3 tools/make_english.py
제품 공부 n회를 들은 바로 뒤에 농업영어 n회가 이어진다. 한 주 다섯 회 가운데 세 회는 표현과 제품 소개(Lv1~3),
두 회는 바이어 질문 응대(Lv4). 제품 이름은 영어 목소리가 한국어처럼 읽도록 소리 나는 대로 적는다.
근거: 농업영어-컨셉.md, 바이오플랜 제품 숙지 원고. 병을 고친다·막는다는 말은 영어로도 쓰지 않는다."""
import json
import os

# 영어 줄 뒤 따라 말할 틈(초)
REPEAT = 3
THINK = 3

EPISODES = []


def ep(n, title, kind, vocab=(), pitch=(), say=None, qa=(), tip=None):
    EPISODES.append(dict(n=n, title=title, kind=kind, vocab=list(vocab), pitch=list(pitch), say=say, qa=list(qa), tip=tip))


# ── 1주: 말하는 법과 대표 제품 ─────────────────────────────
ep(1, '우리 제품을 영어로 소개하는 첫 문장', 'learn',
   vocab=[('fertilizer', '비료'), ('organic input material', '유기농업자재'), ('pesticide', '농약'),
          ('plant vigor', '수세, 작물의 활력'), ('trial result', '시험 결과')],
   pitch=[('Our products are not pesticides.', '우리 제품은 농약이 아닙니다.'),
          ('They are fertilizers or organic input materials.', '비료이거나 유기농업자재입니다.'),
          ('We talk about the crop, not the disease.', '병이 아니라 작물을 이야기합니다.')],
   say=('It cures plant diseases.', 'It helps the crop recover its vigor.', '병을 고친다 대신, 작물이 활력을 되찾도록 돕는다.'))

ep(2, '바꿔 말하기를 영어로', 'learn',
   vocab=[('growth stress', '생육 스트레스'), ('tissue', '조직'), ('patent pending', '특허 출원 중'),
          ('registered patent', '등록 특허'), ('mixing compatibility', '혼용성')],
   pitch=[('It reduces growth stress and keeps plant tissue firm.', '생육 스트레스를 줄이고 작물 조직을 단단하게 관리합니다.'),
          ('It mixes well with pesticides, but please check the mixing chart first.', '농약과 혼용성이 좋지만, 먼저 혼용표를 확인하세요.'),
          ('The patent is pending.', '특허 출원 중입니다.')],
   say=('It makes pesticides work better.', 'It mixes well with pesticides.', '농약 효과를 올린다 대신, 농약과 혼용성이 좋다.'))

ep(3, '생육 단계와 여덟 제품', 'learn',
   vocab=[('seedling', '모, 어린 묘'), ('tillering', '분얼, 새끼치기'), ('heading', '출수, 이삭이 나옴'),
          ('grain filling', '등숙, 알이 여묾'), ('foliar spray', '엽면 살포')],
   pitch=[('For rice, Eeoh-day is for seedlings, and Boon-ul Bahk-sah is for tillering.', '벼에는 모 키울 때 이오데이, 새끼칠 때 분얼박사.'),
          ('Ek-toh-sheen supports flowering, and Tuh-guh supports grain filling.', '엑토신은 꽃, 터거는 알 여물기를 돕습니다.'),
          ('Three granule products care for the soil, and the liquid products are sprayed on the leaves.', '알갱이 세 제품은 땅을, 액상 제품은 잎에 뿌립니다.')],
   say=('This product is good for every crop.', 'Which crop do you grow, and what stage is it in now?', '아무 작물에나 좋다 대신, 작물과 생육 단계부터 묻는다.'))

ep(4, '바이어 질문, 엑토신', 'qa',
   qa=[('What is Ek-toh-sheen made of?', 'It is a liquid foliar fertilizer with more than thirty percent amino acids.', '아미노산 삼십 퍼센트 이상인 액상 엽면시비제입니다.'),
       ('How does it work?', 'It increases glycolysis in the plant, so flowers become stronger and pollination improves.', '해당작용을 늘려 꽃을 강하게 하고 수분과 수정을 돕습니다.'),
       ('How much area does one bottle cover?', 'It depends on the sprayer. Just keep the dilution ratio of one thousand times.', '살포기마다 다르니, 천 배 희석만 지키시면 됩니다.')])

ep(5, '바이어 질문, 엑토신을 벼에', 'qa',
   qa=[('When should farmers spray it on rice?', 'Twice: thirty days before heading, and again at heading.', '출수 삼십 일 전에 한 번, 출수 때 한 번입니다.'),
       ('What if the rice may fall over?', 'If lodging is a concern, spray it stronger, at five hundred times.', '도복이 걱정되면 오백 배로 진하게 뿌립니다.'),
       ('What is the best time of day?', 'Early morning is best. In the evening, spray within about an hour after sunset.', '새벽이 가장 좋고, 저녁이면 해 지고 한 시간 안팎입니다.')])

# ── 2주: 엑토신과 터거, 벼 전용 제품 ───────────────────────
ep(6, '엑토신, 밭과 과수와 근거', 'learn',
   vocab=[('bud break', '발아, 싹 틈'), ('flowering', '개화'), ('pollination', '수분'),
          ('fruit set', '착과'), ('control plot', '대조구')],
   pitch=[('In orchards, spray it on the trunk and branches before bud break, at five hundred times.', '과수는 싹 트기 전, 오백 배로 줄기와 가지에 뿌립니다.'),
          ('In a 2020 trial by the Apple Research Institute, cold damage to flower buds was reduced by 24.8 percent.', '사과연구소 이천이십 년 시험에서 저온기 꽃눈 피해가 이십사 점 팔 퍼센트 줄었습니다.'),
          ('Do not mix it with lime sulfur, machine oil or copper products.', '석회유황합제, 기계유, 구리제와는 섞지 않습니다.')],
   say=('It prevents cold damage.', 'A trial showed less cold damage. Let me tell you the source.', '냉해를 예방한다 대신, 시험에서 저온 피해가 줄었다며 출처를 댄다.'))

ep(7, '터거, 정체와 벼', 'learn',
   vocab=[('transpiration', '증산작용'), ('osmotic pressure', '삼투압'), ('brown seaweed extract', '갈조류 해조추출물'),
          ('fulvic acid', '풀빅산'), ('thousand-grain weight', '천립중')],
   pitch=[('Tuh-guh is a liquid foliar fertilizer made from fermented brown seaweed extract and fulvic acid.', '터거는 갈조류 해조추출물을 발효하고 풀빅산을 더한 액상 엽면시비제입니다.'),
          ('It helps regulate transpiration and supports grain filling.', '증산작용 조절을 도와 알 여물기를 돕습니다.'),
          ('Spray it fifteen to thirty days after heading, at one thousand times.', '출수 후 십오 일에서 삼십 일 사이, 천 배로 뿌립니다.')],
   say=("It's a patented technology.", 'The patent is pending.', '특허 기술이다 대신, 특허 출원 중이다.'))

ep(8, '터거, 열매를 영어로', 'learn',
   vocab=[('fruit enlargement', '과실 비대'), ('firmness', '경도, 단단함'), ('coloration', '착색'),
          ('fruit cracking', '열과'), ('early harvest', '조기 수확')],
   pitch=[('It supports fruit size, firmness and coloration before harvest.', '수확 전 과실 크기와 경도, 착색을 돕습니다.'),
          ('For apples and pears, spray from late May to early July, and again from mid August to early September.', '사과와 배는 오월 말에서 칠월 초, 그리고 팔월 중순에서 구월 초입니다.'),
          ('For grapes, we follow the growth stage, not the calendar.', '포도는 날짜가 아니라 생육 상태를 기준으로 합니다.')],
   say=('It improves coloration by thirty percent.', 'It helps coloration. We do not have measured numbers yet.', '착색이 몇 퍼센트 좋아진다 대신, 착색을 돕고 정량 수치는 아직 없다.'))

ep(9, '바이어 질문, 이오데이', 'qa',
   qa=[('What is Eeoh-day for?', 'It is for soaking rice seeds and growing healthy seedlings.', '볍씨 침종과 건강한 모 키우기 전용입니다.'),
       ('What dilution do you use for seed soaking?', 'Two thousand times, for forty-eight hours. For the nursery, one thousand times.', '침종은 이천 배, 마흔여덟 시간, 모판은 천 배입니다.'),
       ('Can I mix it with pesticides?', 'It mixes well, but never with alkaline products, because it is strongly acidic.', '혼용성은 좋지만 강산성이라 알칼리성 약제와는 절대 안 됩니다.')])

ep(10, '바이어 질문, 분얼박사', 'qa',
   qa=[('How do I apply Boon-ul Bahk-sah?', 'Spread it on the seedling trays up to three days before transplanting.', '이앙 사흘 전부터 당일 사이에 모판에 뿌립니다.'),
       ('What if I missed that timing?', 'You can spread it on the paddy within seven days after transplanting.', '이앙 뒤 칠 일 안에 논에 뿌릴 수 있습니다.'),
       ('How many more tillers will I get?', 'Usually ten to twenty percent more, depending on the variety and the field.', '보통 십에서 이십 퍼센트, 품종과 환경에 따라 다릅니다.')])

# ── 3주: 작물군 전용 제품 ─────────────────────────────────
ep(11, '분얼박사와 저탄소 벼농사', 'learn',
   vocab=[('effective tillers', '유효분얼'), ('lodging', '도복, 쓰러짐'), ('early drainage', '조기 물떼기'),
          ('methane emissions', '메탄 배출'), ('reduction target', '감축 목표')],
   pitch=[('Boon-ul Bahk-sah helps rice form effective tillers in a short period.', '분얼박사는 짧은 기간에 유효분얼이 생기도록 돕습니다.'),
          ('The lower stem gets shorter and thicker, so the plant resists lodging.', '아래 마디가 짧고 굵어져 쓰러짐에 강해집니다.'),
          ('Early drainage contributes to the government target for lower methane emissions.', '조기 물떼기로 정부의 메탄 감축 목표에 기여합니다.')],
   say=('It cuts methane by 20.9 percent.', 'The government target is 20.9 percent, and our method contributes to it.', '메탄을 이십 점 구 퍼센트 줄인다 대신, 그것은 정부 목표이고 우리는 기여한다.'))

ep(12, '규산골드', 'learn',
   vocab=[('soluble silicate', '수용성 규산'), ('cell wall', '세포벽'), ('alkaline', '알칼리성'),
          ('acidic', '산성'), ('shake well', '잘 흔들다')],
   pitch=[('Kyoo-sahn Gold is a liquid organic input material with twenty percent soluble silicate.', '규산골드는 수용성 규산 이십 퍼센트의 액상 유기농업자재입니다.'),
          ('Silicate builds up in the tissue, so the cell walls and stems become firm.', '규산이 조직에 쌓여 세포벽과 줄기가 단단해집니다.'),
          ('Never mix it with acidic products. Spray it separately.', '산성 제품과는 절대 섞지 말고 따로 뿌립니다.')],
   say=('It is registered for disease control.', 'It is an organic input material, not a registered pesticide.', '병해 방제용이다 대신, 농약 등록이 아닌 유기농업자재다.'))

ep(13, '한번애뿌리', 'learn',
   vocab=[('pre-planting soil treatment', '심기 전 토양 처리'), ('rooting', '뿌리 내림'), ('soil pH', '토양 산도'),
          ('salt concentration', '염류 농도'), ('waterlogging', '습해, 침수')],
   pitch=[('Hahn-bun-ae Ppoo-ree is applied once to the soil before sowing or transplanting.', '한번애뿌리는 파종이나 정식 전에 한 번 땅에 넣습니다.'),
          ('It prepares the soil so the roots can settle quickly.', '흙을 미리 정리해 뿌리가 빨리 자리 잡게 합니다.'),
          ('One ten-kilogram bag covers about four hundred pyeong of open field.', '십 킬로그램 한 포대로 노지 약 사백 평입니다.')],
   say=('It solves salt build-up in one application.', 'It works toward lowering salt concentration.', '염류를 한 번에 해결한다 대신, 염류 농도를 낮추는 방향으로 작용한다.'))

ep(14, '바이어 질문, 녹색탄', 'qa',
   qa=[('What does Nohk-saek-tahn do?', 'It helps keep photosynthesis going under low light, low temperature or waterlogging.', '빛이 모자라거나 춥거나 습해일 때 광합성이 유지되도록 돕습니다.'),
       ('There are spots on the leaves. Will this cure them?', 'It is not a treatment for disease. Please control the disease separately, and use it for leaf vigor.', '병 치료제가 아닙니다. 방제는 따로 하시고 잎 활력용으로 쓰세요.'),
       ('Can I use it with Ek-toh-sheen?', 'Yes. Ek-toh-sheen takes care of flowers, and Nohk-saek-tahn takes care of leaves.', '네, 엑토신은 꽃을, 녹색탄은 잎을 맡습니다.')])

ep(15, '바이어 질문, 조피박사', 'qa',
   qa=[('When should I apply Joh-pee Bahk-sah?', 'During dormancy, from November to March. Add one more in summer if the tree is weak.', '휴면기인 십일월에서 삼월, 나무가 약하면 여름에 한 번 더.'),
       ('How do I apply it?', 'Spread it in a circle thirty centimeters away from the trunk.', '줄기에서 삼십 센티미터 띄워 둥글게 뿌립니다.'),
       ('Will the rough bark really come off? How long does it take?', 'It improves the tree slowly, so please watch it for about a year.', '나무를 천천히 개선하니 일 년 정도 지켜봐 주세요.')])

# ── 4주: 응대 심화와 실전 ─────────────────────────────────
ep(16, '섞으면 안 되는 조합', 'learn',
   vocab=[('tank mix', '한 통에 섞어 뿌리기'), ('strongly acidic', '강산성'), ('lime sulfur', '석회유황합제'),
          ('spray immediately', '바로 뿌리다'), ('leftover solution', '남은 약액')],
   pitch=[('Eeoh-day and Nohk-saek-tahn are strongly acidic, so never mix them with alkaline products.', '이오데이와 녹색탄은 강산성이라 알칼리성 약제와 절대 섞지 않습니다.'),
          ('Kyoo-sahn Gold is alkaline, so it always goes alone.', '규산골드는 알칼리 쪽이라 늘 혼자 뿌립니다.'),
          ('If you must mix, spray immediately and do not reuse the leftover solution.', '꼭 섞어야 하면 바로 뿌리고 남은 약액은 다시 쓰지 않습니다.')],
   say=('You can mix it with anything.', 'It mixes well, but not with alkaline products. Please check each combination with us.', '다 섞어도 된다 대신, 조건을 붙이고 조합은 확인받게 한다.'))

ep(17, '친환경 농가와 등록, 특허', 'learn',
   vocab=[('organic farming', '유기 재배'), ('listed for organic use', '유기농업자재로 공시된'), ('efficacy', '효과'),
          ('certification body', '인증기관'), ('list price', '기준 가격')],
   pitch=[('Being listed for organic use means it can be used in organic farming. It does not certify efficacy.', '공시는 유기재배에 쓸 수 있다는 뜻이지 효과 인증이 아닙니다.'),
          ('For organic farms, we offer the Gold versions of Ek-toh-sheen, Tuh-guh and Eeoh-day.', '친환경 농가에는 엑토신, 터거, 이오데이의 골드 제품을 안내합니다.'),
          ('Tuh-guh is the only product whose patent is still pending.', '특허가 아직 출원 중인 제품은 터거 하나입니다.')],
   say=('It is certified, so it works.', 'It is listed for organic use. That is not a certification of efficacy.', '공시받았으니 효과가 있다 대신, 공시는 효과 인증이 아니다.'))

ep(18, '숫자를 영어로 말하기', 'learn',
   vocab=[('reduced by', '만큼 줄었다'), ('increased by', '만큼 늘었다'), ('yield', '수확량'),
          ('in-house data', '자사 자료'), ('source', '출처')],
   pitch=[('According to a 2016 study in a Heilongjiang agricultural journal, soybean yield increased by 23.5 percent.', '흑룡강성 농업과학 학술지 이천십육 년 연구에서 콩 수확량이 이십삼 점 오 퍼센트 늘었습니다.'),
          ('This is in-house data from our research institute.', '이것은 우리 연구소의 자사 자료입니다.'),
          ('We do not have measured data for this product yet, so let me explain how it works.', '이 제품은 아직 정량 자료가 없으니 원리로 설명드리겠습니다.')],
   say=('It will raise your rice grade.', 'We cannot promise a higher grade.', '쌀 등급이 오른다 대신, 등급 상승은 약속할 수 없다.'))

ep(19, '바이어 질문, 벼 농가의 한 해', 'qa',
   qa=[('I transplanted ten days ago. Can I still use Boon-ul Bahk-sah?', 'It is past the seven-day window, so the effect may be lower. Let me check with our research institute.', '칠 일을 넘겨 효과가 떨어질 수 있으니 연구소에 확인하겠습니다.'),
       ('The leaves turned yellow after I sprayed Tuh-guh. Is that a problem?', 'No. Nutrients are moving from the leaves into the grain. It means the grain is filling well.', '잎의 양분이 벼알로 옮겨 가는 것으로, 잘 여물고 있다는 뜻입니다.'),
       ('Can I put Kyoo-sahn Gold and Ek-toh-sheen in the same tank?', 'No. Kyoo-sahn Gold is alkaline and Ek-toh-sheen is acidic. Spray them separately.', '안 됩니다. 알칼리와 산성이라 따로 뿌립니다.')])

ep(20, '바이어 질문, 과수와 고추 농가, 마무리', 'qa',
   qa=[('Which product do you recommend for my apple orchard?', 'First, tell me the growth stage now. Then I can recommend the right product.', '먼저 지금 생육 단계를 알려 주시면 맞는 제품을 권해 드리겠습니다.'),
       ('What should I use before planting peppers?', 'Apply Hahn-bun-ae Ppoo-ree to the soil before transplanting, one bag for about four hundred pyeong.', '정식 전에 한번애뿌리를, 한 포대에 약 사백 평으로 넣습니다.'),
       ('Does it prevent disease?', 'It is not a pesticide. It helps the crop stay vigorous and reduces growth stress.', '농약이 아닙니다. 작물 활력을 지키고 생육 스트레스를 줄입니다.')],
   tip='사 주 동안의 농업영어를 마칩니다. 다음 주부터는 첫 회로 돌아갑니다. 두 번째에는 한국어 뜻을 듣고 영어가 나오기 전에 먼저 말해 보세요.')


def ko(t, pause=0):
    return {'ko': t, **({'pause': pause} if pause else {})}


def en(t, pause=0):
    return {'en': t, **({'pause': pause} if pause else {})}


def lines_of(e, prev):
    out = [ko(f"이어서 농업영어 {e['n']}회, {e['title']}입니다.")]
    if prev and prev['vocab']:
        out.append(ko('먼저 지난 시간 복습입니다. 한국어 뜻을 듣고 영어로 말해 보세요.'))
        for word, mean in prev['vocab'][:3]:
            out.append(ko(mean + '.', THINK))
            out.append(en(word + '.'))
    elif prev and prev['qa']:
        out.append(ko('먼저 지난 시간 복습입니다. 이 질문에 영어로 답해 보세요.'))
        q, a, _ = prev['qa'][0]
        out.append(en(q, THINK))
        out.append(en(a))
    if e['kind'] == 'learn':
        out.append(ko('오늘의 표현 다섯 개입니다. 영어를 듣고, 뜻을 듣고, 한 번 더 따라 해 보세요.'))
        for word, mean in e['vocab']:
            out.append(en(word + '.'))
            out.append(ko(mean + '.'))
            out.append(en(word + '.', 2))
        out.append(ko('이제 제품을 영어로 소개해 보겠습니다. 문장마다 따라 말해 보세요.'))
        for s, mean in e['pitch']:
            out.append(en(s))
            out.append(ko(mean))
            out.append(en(s, REPEAT))
        if e['say']:
            dont, do, mean = e['say']
            out.append(ko('마지막으로, 이렇게 말하지 마세요.'))
            out.append(en(dont))
            out.append(ko('이렇게 말하세요.'))
            out.append(en(do, REPEAT))
            out.append(ko(mean))
    else:
        out.append(ko('오늘은 바이어 질문에 영어로 답하는 연습입니다. 질문을 듣고, 잠깐 답을 떠올려 본 뒤 모범 답을 들어 보세요.'))
        for i, (q, a, mean) in enumerate(e['qa']):
            out.append(ko(['첫 번째 질문입니다.', '두 번째 질문입니다.', '세 번째 질문입니다.'][i]))
            out.append(en(q, THINK))
            out.append(en(a))
            out.append(ko(mean))
            out.append(en(a, REPEAT))
    # 끝으로 오늘 문장을 영어로만 한 번 더 (뜻 없이, 따라 말할 틈만)
    if e['kind'] == 'learn':
        out.append(ko('오늘 소개 문장을 영어로만 한 번 더 듣겠습니다.'))
        for s, _ in e['pitch']:
            out.append(en(s, REPEAT))
    else:
        out.append(ko('오늘 질문을 한 번 더 듣겠습니다. 이번에는 영어로 먼저 답해 보세요.'))
        for q, a, _ in e['qa']:
            out.append(en(q, THINK + 2))
            out.append(en(a, 2))
    out.append(ko(e['tip'] if e['tip'] else '오늘 농업영어는 여기까지입니다.'))
    return out


def main():
    eps = []
    prev = None
    for e in EPISODES:
        eps.append({'n': e['n'], 'title': e['title'], 'lines': lines_of(e, prev)})
        prev = e
    data = {
        '_설명': '농업영어 원고. tools/make_english.py가 만든다 (직접 고치지 말고 그 파일을 고친 뒤 다시 실행). '
                 '줄마다 ko(한국어 목소리) 또는 en(영어 목소리), pause는 줄 뒤에 쉬는 초(따라 말할 틈).',
        'title': '농업영어',
        'voice': 'en-US-AndrewNeural',
        'episodes': eps,
    }
    path = os.path.join(os.path.dirname(__file__), '..', 'lessons', 'english.json')
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write('\n')


if __name__ == '__main__':
    main()
