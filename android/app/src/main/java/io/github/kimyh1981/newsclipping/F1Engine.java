package io.github.kimyh1981.newsclipping;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Supertonic-3 음성(F1)을 폰 안에서 만든다. 주인이 고른 목소리 (2026-10-16, 서버 녹음 대신 폰에서 실시간).
 * 개발사 Java 예제(supertone-inc/supertonic java/Helper.java)를 그대로 옮기고, 글 다듬기는 F1Text, 모델 받기는 F1Voice에 둔다.
 * 안드로이드에 기대지 않아서 JVM 단위 테스트에서도 같은 코드를 돌린다.
 */
final class F1Engine implements AutoCloseable {
    static final String STYLE = "F1";
    static final int STEPS = 8; // 품질 5~12, 개발사 기본 8 (GitHub 서버 실측: 8단계면 실시간의 2.3배)

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession dp, textEnc, vectorEst, vocoder;
    private final OnnxTensor styleTtl, styleDp;
    private final long[] indexer;
    final int sampleRate;
    private final int baseChunkSize, chunkCompress, latentDim;
    private final Random rng = new Random();

    /** dir: onnx/ 와 voice_styles/ 가 든 폴더 (허깅페이스 supertone-oss-archive/supertonic-3 와 같은 모양) */
    F1Engine(File dir) throws Exception { // 안드로이드의 JSONException은 확인 예외라 함께 넘긴다
        JSONObject cfg = json(new File(dir, "onnx/tts.json"));
        sampleRate = cfg.getJSONObject("ae").getInt("sample_rate");
        baseChunkSize = cfg.getJSONObject("ae").getInt("base_chunk_size");
        chunkCompress = cfg.getJSONObject("ttl").getInt("chunk_compress_factor");
        latentDim = cfg.getJSONObject("ttl").getInt("latent_dim");
        OrtSession.SessionOptions o = new OrtSession.SessionOptions();
        o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        File onnx = new File(dir, "onnx");
        dp = env.createSession(new File(onnx, "duration_predictor.onnx").getPath(), o);
        textEnc = env.createSession(new File(onnx, "text_encoder.onnx").getPath(), o);
        vectorEst = env.createSession(new File(onnx, "vector_estimator.onnx").getPath(), o);
        vocoder = env.createSession(new File(onnx, "vocoder.onnx").getPath(), o);
        JSONArray idx = new JSONArray(read(new File(onnx, "unicode_indexer.json")));
        indexer = new long[idx.length()];
        for (int i = 0; i < indexer.length; i++) indexer[i] = idx.getLong(i);
        JSONObject st = json(new File(dir, "voice_styles/" + STYLE + ".json"));
        styleTtl = styleTensor(st.getJSONObject("style_ttl"));
        styleDp = styleTensor(st.getJSONObject("style_dp"));
    }

    static final float LEAD_SEC = 0.2f;

    /** 한 줄을 소리(-1~1, 한 채널)로. 우리말은 한 덩이 120자까지, 덩이 사이는 0.3초 쉰다 (개발사 기본) */
    float[] synth(String text, String lang, float speed) throws OrtException {
        List<String> chunks = F1Text.chunks(text, "ko".equals(lang) ? 120 : 300);
        int silence = (int) (0.3f * sampleRate);
        int lead = (int) (LEAD_SEC * sampleRate); // 앞을 비워 둔다: 차 스피커가 소리를 받기 시작하며 첫소리를 먹었다 (2026-10-19 출근길, 영어 단어가 통째로 빠짐)
        List<float[]> parts = new ArrayList<>();
        int total = lead;
        for (int i = 0; i < chunks.size(); i++) {
            float[] w = infer(chunks.get(i), lang, speed);
            if (i > 0) total += silence;
            parts.add(w);
            total += w.length;
        }
        float[] out = new float[total];
        int at = lead;
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) at += silence;
            System.arraycopy(parts.get(i), 0, out, at, parts.get(i).length);
            at += parts.get(i).length;
        }
        return out;
    }

    private float[] infer(String chunk, String lang, float speed) throws OrtException {
        int[] cps = F1Text.prepare(chunk, lang).codePoints().toArray();
        long[] ids = new long[cps.length];
        for (int j = 0; j < cps.length; j++) ids[j] = cps[j] < indexer.length ? indexer[cps[j]] : 0;
        float[] textMask = new float[cps.length];
        Arrays.fill(textMask, 1f);
        try (OnnxTensor idsT = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), new long[] {1, ids.length});
             OnnxTensor maskT = OnnxTensor.createTensor(env, FloatBuffer.wrap(textMask), new long[] {1, 1, cps.length})) {
            Map<String, OnnxTensor> in = new HashMap<>();
            in.put("text_ids", idsT);
            in.put("style_dp", styleDp);
            in.put("text_mask", maskT);
            float duration;
            try (OrtSession.Result r = dp.run(in)) {
                Object v = r.get(0).getValue();
                duration = (v instanceof float[][] ? ((float[][]) v)[0] : (float[]) v)[0] / speed;
            }
            in.clear();
            in.put("text_ids", idsT);
            in.put("style_ttl", styleTtl);
            in.put("text_mask", maskT);
            try (OrtSession.Result enc = textEnc.run(in)) {
                OnnxTensor emb = (OnnxTensor) enc.get(0);
                long wavLen = (long) (duration * sampleRate);
                int chunkSize = baseChunkSize * chunkCompress;
                int latentLen = (int) ((wavLen + chunkSize - 1) / chunkSize);
                int dim = latentDim * chunkCompress;
                // 잡음에서 시작해 STEPS번 다듬는다 (Box-Muller 정규분포). 길이는 예측한 길이 그대로라 마스크는 모두 1
                float[] xt = new float[dim * latentLen];
                for (int k = 0; k < xt.length; k++) {
                    double u1 = Math.max(1e-10, rng.nextDouble()), u2 = rng.nextDouble();
                    xt[k] = (float) (Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2));
                }
                float[] latentMask = new float[latentLen];
                Arrays.fill(latentMask, 1f);
                try (OnnxTensor total = OnnxTensor.createTensor(env, new float[] {STEPS});
                     OnnxTensor lm = OnnxTensor.createTensor(env, FloatBuffer.wrap(latentMask), new long[] {1, 1, latentLen})) {
                    for (int step = 0; step < STEPS; step++) {
                        try (OnnxTensor cur = OnnxTensor.createTensor(env, new float[] {step});
                             OnnxTensor noisy = OnnxTensor.createTensor(env, FloatBuffer.wrap(xt), new long[] {1, dim, latentLen})) {
                            Map<String, OnnxTensor> v = new HashMap<>();
                            v.put("noisy_latent", noisy);
                            v.put("text_emb", emb);
                            v.put("style_ttl", styleTtl);
                            v.put("latent_mask", lm);
                            v.put("text_mask", maskT);
                            v.put("current_step", cur);
                            v.put("total_step", total);
                            try (OrtSession.Result r = vectorEst.run(v)) {
                                xt = flat((float[][][]) r.get(0).getValue());
                            }
                        }
                    }
                }
                try (OnnxTensor lat = OnnxTensor.createTensor(env, FloatBuffer.wrap(xt), new long[] {1, dim, latentLen});
                     OrtSession.Result r = vocoder.run(java.util.Collections.singletonMap("latent", lat))) {
                    float[] wav = ((float[][]) r.get(0).getValue())[0];
                    return Arrays.copyOf(wav, (int) Math.min(wav.length, wavLen)); // 예측한 길이만큼 (끝의 군소리를 자른다)
                }
            }
        }
    }

    /** 16비트 한 채널 WAV로 저장 (MediaPlayer가 바로 튼다) */
    static void writeWav(float[] pcm, int rate, File to) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(44 + pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length * 2).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length * 2);
        for (float s : pcm) b.putShort((short) Math.max(-32768, Math.min(32767, s * 32767)));
        File part = new File(to.getPath() + ".part");
        try (OutputStream o = new FileOutputStream(part)) { o.write(b.array()); }
        if (!part.renameTo(to)) throw new IOException("저장 실패 " + to);
    }

    @Override public void close() {
        for (AutoCloseable c : new AutoCloseable[] {styleTtl, styleDp, dp, textEnc, vectorEst, vocoder}) {
            try { c.close(); } catch (Exception ignored) { }
        }
    }

    private OnnxTensor styleTensor(JSONObject s) throws Exception {
        JSONArray dims = s.getJSONArray("dims");
        long[] shape = {dims.getLong(0), dims.getLong(1), dims.getLong(2)};
        float[] data = new float[(int) (shape[0] * shape[1] * shape[2])];
        JSONArray a = s.getJSONArray("data");
        int k = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONArray rows = a.getJSONArray(i);
            for (int j = 0; j < rows.length(); j++) {
                JSONArray row = rows.getJSONArray(j);
                for (int m = 0; m < row.length(); m++) data[k++] = (float) row.getDouble(m);
            }
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape);
    }

    private static float[] flat(float[][][] a) {
        int d1 = a[0].length, d2 = a[0][0].length;
        float[] out = new float[a.length * d1 * d2];
        int k = 0;
        for (float[][] x : a) for (float[] y : x) { System.arraycopy(y, 0, out, k, d2); k += d2; }
        return out;
    }

    private static String read(File f) throws IOException { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
    private static JSONObject json(File f) throws Exception { return new JSONObject(read(f)); }
}
