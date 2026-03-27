package com.sookmyung.medicell;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

public class PillClassifier {

    private static final String TAG        = "PillClassifier";
    private static final int    INPUT_SIZE = 224;
    private static final int    FEAT_DIM   = 576;
    private static final int    TOP_K      = 5;

    private static final String STAGE1_FILE = "stage1.tflite";

    private static final Map<Integer, String> STAGE2_FEAT_FILES =
            new HashMap<Integer, String>() {{
                put(0, "stage2_circle_feat.tflite");
                put(1, "stage2_oval_feat.tflite");
                put(2, "stage2_oblong_feat.tflite");
                put(3, "stage2_other_feat.tflite");
            }};

    private static final Map<Integer, String> DB_FILES =
            new HashMap<Integer, String>() {{
                put(0, "circle_db.json");
                put(1, "oval_db.json");
                put(2, "oblong_db.json");
                put(3, "other_db.json");
            }};

    private final Interpreter                     stage1Interpreter;
    private final HashMap<Integer, Interpreter>   featInterpreters = new HashMap<>();
    private final HashMap<Integer, List<DbEntry>> featureDbs       = new HashMap<>();


    public PillClassifier(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);

        stage1Interpreter = new Interpreter(
                loadModelFile(context, STAGE1_FILE), options);
        Log.d(TAG, "Stage1 로드 완료");

        for (int shape = 0; shape < 4; shape++) {
            String featFile = STAGE2_FEAT_FILES.get(shape);
            String dbFile   = DB_FILES.get(shape);

            if (featFile != null) {
                featInterpreters.put(shape,
                        new Interpreter(
                                loadModelFile(context, featFile), options));
                Log.d(TAG, "Stage2 로드 완료: shape=" + shape);
            }
            if (dbFile != null)
                featureDbs.put(shape, loadFeatureDb(context, dbFile));
        }
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 메인 추론
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    public InferenceResult classify(Bitmap bitmap) {
        if (bitmap == null)
            return new InferenceResult(null, Collections.emptyList());

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Log.d(TAG, "비트맵: " + w + "x" + h
                + "  config=" + bitmap.getConfig());

        Bitmap soft = bitmap.copy(Bitmap.Config.ARGB_8888, false);

        // 중앙 픽셀 확인
        int[] px = new int[1];
        soft.getPixels(px, 0, w, w/2, h/2, 1, 1);
        Log.d(TAG, "중앙 픽셀: R=" + ((px[0]>>16)&0xFF)
                + " G=" + ((px[0]>>8)&0xFF)
                + " B=" + (px[0]&0xFF));

        Bitmap resized = Bitmap.createScaledBitmap(
                soft, INPUT_SIZE, INPUT_SIZE, true);
        resized = resized.copy(Bitmap.Config.ARGB_8888, false);

        ByteBuffer input = bitmapToInput(resized);

        // Stage1
        float[][] out1     = new float[1][4];
        stage1Interpreter.run(input, out1);
        float[] shapeProbs = out1[0];
        int     predShape  = argmax(shapeProbs);

        Log.d(TAG, "Stage1: shape=" + shapeName(predShape)
                + "  prob=" + shapeProbs[predShape]);
        Log.d(TAG, "Stage1 전체: circle=" + shapeProbs[0]
                + " oval=" + shapeProbs[1]
                + " oblong=" + shapeProbs[2]
                + " other=" + shapeProbs[3]);

        Stage1Result s1 = new Stage1Result(
                shapeProbs, predShape, shapeProbs[predShape]);

        List<Prediction> top5 = runRetrieval(resized, predShape);

        return new InferenceResult(s1, top5);
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Retrieval
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private List<Prediction> runRetrieval(Bitmap bmp, int shape) {
        Interpreter   it = featInterpreters.get(shape);
        List<DbEntry> db = featureDbs.get(shape);

        if (it == null || db == null || db.isEmpty()) {
            Log.w(TAG, "shape=" + shape + " 모델 또는 DB 없음");
            return Collections.emptyList();
        }

        ByteBuffer input   = bitmapToInput(bmp);
        float[][]  outFeat = new float[1][FEAT_DIM];
        it.run(input, outFeat);
        float[] feat = l2Normalize(outFeat[0]);

        Log.d(TAG, "feat 첫 5개: "
                + String.format("%.4f,%.4f,%.4f,%.4f,%.4f",
                feat[0], feat[1], feat[2], feat[3], feat[4]));

        List<Prediction> all = new ArrayList<>();
        for (DbEntry entry : db) {
            float sim = dotProduct(feat, entry.feature);
            all.add(new Prediction(entry.itemSeq, sim, shapeName(shape)));
        }

        Collections.sort(all, (a, b) -> Float.compare(b.score, a.score));

        List<Prediction> deduped = new ArrayList<>();
        Set<String>      seen    = new LinkedHashSet<>();
        for (Prediction p : all) {
            if (!seen.contains(p.label)) {
                seen.add(p.label);
                deduped.add(p);
                if (deduped.size() >= TOP_K) break;
            }
        }

        Log.d(TAG, "Retrieval: shape=" + shape
                + "  top1=" + (deduped.isEmpty() ? "없음"
                : deduped.get(0).label)
                + "  score=" + (deduped.isEmpty() ? 0
                : deduped.get(0).score));

        return deduped;
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 전처리 (raw 0-255 전달 → 모델 내부 Rescaling이 처리)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private ByteBuffer bitmapToInput(Bitmap bitmap) {
        ByteBuffer buf = ByteBuffer.allocateDirect(
                4 * INPUT_SIZE * INPUT_SIZE * 3);
        buf.order(ByteOrder.nativeOrder());

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : pixels) {
            buf.putFloat((float)((pixel >> 16) & 0xFF));
            buf.putFloat((float)((pixel >>  8) & 0xFF));
            buf.putFloat((float)( pixel        & 0xFF));
        }

        buf.rewind();
        return buf;
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 수학 유틸
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private int argmax(float[] arr) {
        int best = 0;
        for (int i = 1; i < arr.length; i++)
            if (arr[i] > arr[best]) best = i;
        return best;
    }

    private float[] l2Normalize(float[] v) {
        float norm = 0f;
        for (float x : v) norm += x * x;
        norm = (float) Math.sqrt(norm);
        if (norm < 1e-12f) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] / norm;
        return out;
    }

    private float dotProduct(float[] a, float[] b) {
        float sum = 0f;
        int   len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) sum += a[i] * b[i];
        return sum;
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 파일 로드
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private MappedByteBuffer loadModelFile(Context context,
                                           String name) throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(name);
        FileInputStream     is = new FileInputStream(fd.getFileDescriptor());
        FileChannel         fc = is.getChannel();
        return fc.map(FileChannel.MapMode.READ_ONLY,
                fd.getStartOffset(), fd.getDeclaredLength());
    }

    private List<DbEntry> loadFeatureDb(Context context, String file) {
        List<DbEntry> db = new ArrayList<>();
        try {
            InputStream    is = context.getAssets().open(file);
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(is));
            StringBuilder  sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj     = arr.getJSONObject(i);
                String     itemSeq = obj.getString("item_seq");
                JSONArray  featArr = obj.getJSONArray("feature");
                float[]    feat    = new float[featArr.length()];
                for (int j = 0; j < featArr.length(); j++)
                    feat[j] = (float) featArr.getDouble(j);
                db.add(new DbEntry(itemSeq, feat));
            }
            Log.d(TAG, file + " 로드: " + db.size() + "개 품목");

        } catch (Exception e) {
            Log.e(TAG, "DB 로드 실패: " + file + "  " + e.getMessage(), e);
        }
        return db;
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 데이터 클래스
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    public static class DbEntry {
        public final String  itemSeq;
        public final float[] feature;
        public DbEntry(String itemSeq, float[] feature) {
            this.itemSeq = itemSeq;
            this.feature = feature;
        }
    }

    public static class Prediction {
        public final String label;
        public final float  score;
        public final String source;
        public Prediction(String label, float score, String source) {
            this.label  = label;
            this.score  = score;
            this.source = source;
        }
    }

    public static class Stage1Result {
        public final float[] shapeProbs;
        public final int     predShape;
        public final float   predProb;
        public Stage1Result(float[] probs, int shape, float prob) {
            this.shapeProbs = probs;
            this.predShape  = shape;
            this.predProb   = prob;
        }
    }

    public static class InferenceResult {
        public final Stage1Result     stage1Result;
        public final List<Prediction> top5Predictions;
        public InferenceResult(Stage1Result s1, List<Prediction> top5) {
            this.stage1Result    = s1;
            this.top5Predictions = top5;
        }
    }

    public static String shapeName(int s) {
        switch (s) {
            case 0: return "circle";
            case 1: return "oval";
            case 2: return "oblong";
            case 3: return "other";
            default: return "unknown";
        }
    }

    public void close() {
        stage1Interpreter.close();
        for (Interpreter it : featInterpreters.values()) it.close();
    }
}