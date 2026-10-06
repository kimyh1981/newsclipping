#!/usr/bin/env python3
"""MS 엣지 '소리 내어 읽기' 신경망 음성(edge-tts)으로 여러 줄을 한꺼번에 녹음한다. tools/tts.js가 부른다.
사용: python3 tools/edge_tts_batch.py jobs.json <음성> <빠르기>   jobs.json = [{"id", "text", "file"}]
결과(표준 출력): {"id": null(성공) 또는 "오류 내용"}"""
import asyncio
import json
import os
import sys

import edge_tts


async def one(sem, job, voice, rate):
    err = None
    async with sem:
        for attempt in range(3):
            try:
                part = job["file"] + ".part"  # 끝까지 받은 파일만 이름을 바꾼다: 도중에 멈춰도 반쪽 파일이 남지 않게
                await edge_tts.Communicate(job["text"], voice, rate=rate).save(part)
                os.replace(part, job["file"])
                return job["id"], None
            except Exception as e:  # 연결이 끊기거나 잠깐 막히면 쉬었다가 다시
                err = f"{type(e).__name__}: {str(e)[:120]}"
                await asyncio.sleep(2 * (attempt + 1))
    return job["id"], err


async def main():
    with open(sys.argv[1], encoding="utf-8") as f:
        jobs = json.load(f)
    sem = asyncio.Semaphore(4)
    res = await asyncio.gather(*(one(sem, j, sys.argv[2], sys.argv[3]) for j in jobs))
    json.dump(dict(res), sys.stdout)


asyncio.run(main())
