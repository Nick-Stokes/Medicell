package com.sookmyung.medicell;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class PillClassifier {

    private static final String TAG = "PillClassifier";
    private static final int INPUT_SIZE = 224;
    private static final int NUM_SHAPES = 4;

    // TFLite files (crop_only best float32)
    private static final String STAGE1_FILE = "stage1_crop_only_best_float32.tflite";
    private static final String STAGE2_CIRCLE_FILE = "stage2_circle_crop_only_best_float32.tflite";
    private static final String STAGE2_OVAL_FILE = "stage2_oval_crop_only_best_float32.tflite";
    private static final String STAGE2_OBLONG_FILE = "stage2_oblong_crop_only_best_float32.tflite";
    private static final String STAGE2_OTHER_FILE = "stage2_other_crop_only_best_float32.tflite";

    // labelmap json (crop_only)
    private static final String LABELMAP_CIRCLE_FILE = "stage2_circle_crop_only_labelmap.json";
    private static final String LABELMAP_OVAL_FILE = "stage2_oval_crop_only_labelmap.json";
    private static final String LABELMAP_OBLONG_FILE = "stage2_oblong_crop_only_labelmap.json";
    private static final String LABELMAP_OTHER_FILE = "stage2_other_crop_only_labelmap.json";

    // rule
    private static final float STAGE1_PROB_THRESHOLD = 0.7f;
    private static final float STAGE1_GAP_THRESHOLD = 0.3f;

    private final Interpreter stage1Interpreter;
    private final HashMap<Integer, Interpreter> stage2Interpreters = new HashMap<>();
    private final HashMap<Integer, HashMap<Integer, String>> stage2LabelMaps = new HashMap<>();

    public static class Prediction {
        public final int classIndex;   // local class index in stage2
        public final String label;     // ITEM_SEQ
        public final float score;      // probability
        public final String source;    // circle / oval / oblong / other

        public Prediction(int classIndex, String label, float score, String source) {
            this.classIndex = classIndex;
            this.label = label;
            this.score = score;
            this.source = source;
        }
    }

    public static class Stage1Result {
        public final float[] shapeProbs; // [circle, oval, oblong, other]
        public final int top1Class;
        public final int top2Class;
        public final float top1Prob;
        public final float top2Prob;
        public final boolean runOtherAlso;

        public Stage1Result(float[] shapeProbs, int top1Class, int top2Class,
                            float top1Prob, float top2Prob, boolean runOtherAlso) {
            this.shapeProbs = shapeProbs;
            this.top1Class = top1Class;
            this.top2Class = top2Class;
            this.top1Prob = top1Prob;
            this.top2Prob = top2Prob;
            this.runOtherAlso = runOtherAlso;
        }
    }

    public static class InferenceResult {
        public final Stage1Result stage1Result;
        public final List<Prediction> top5Predictions;

        public InferenceResult(Stage1Result stage1Result, List<Prediction> top5Predictions) {
            this.stage1Result = stage1Result;
            this.top5Predictions = top5Predictions;
        }
    }

    public PillClassifier(Context context) throws IOException {
        Log.d(TAG, "PillClassifier init start");

        stage1Interpreter = new Interpreter(loadModelFile(context, STAGE1_FILE));
        Log.d(TAG, "Loaded stage1: " + STAGE1_FILE);

        stage2Interpreters.put(0, new Interpreter(loadModelFile(context, STAGE2_CIRCLE_FILE)));
        stage2Interpreters.put(1, new Interpreter(loadModelFile(context, STAGE2_OVAL_FILE)));
        stage2Interpreters.put(2, new Interpreter(loadModelFile(context, STAGE2_OBLONG_FILE)));
        stage2Interpreters.put(3, new Interpreter(loadModelFile(context, STAGE2_OTHER_FILE)));

        stage2LabelMaps.put(0, loadLabelMap(context, LABELMAP_CIRCLE_FILE));
        stage2LabelMaps.put(1, loadLabelMap(context, LABELMAP_OVAL_FILE));
        stage2LabelMaps.put(2, loadLabelMap(context, LABELMAP_OBLONG_FILE));
        stage2LabelMaps.put(3, loadLabelMap(context, LABELMAP_OTHER_FILE));

        Log.d(TAG, "All stage2 models + labelmaps loaded");

        // output size debug
        logInterpreterShapes();
    }

    private void logInterpreterShapes() {
        try {
            int[] s1In = stage1Interpreter.getInputTensor(0).shape();
            int[] s1Out = stage1Interpreter.getOutputTensor(0).shape();
            Log.d(TAG, "stage1 input shape=" + shapeToString(s1In) + ", output shape=" + shapeToString(s1Out));
        } catch (Exception e) {
            Log.e(TAG, "Failed to log stage1 tensor shapes", e);
        }

        for (int sc = 0; sc <= 3; sc++) {
            try {
                Interpreter it = stage2Interpreters.get(sc);
                HashMap<Integer, String> map = stage2LabelMaps.get(sc);
                if (it == null) continue;
                int[] in = it.getInputTensor(0).shape();
                int[] out = it.getOutputTensor(0).shape();
                int outClasses = (out.length >= 2) ? out[out.length - 1] : -1;
                int mapSize = (map == null) ? -1 : map.size();

                Log.d(TAG,
                        "stage2 " + shapeName(sc) +
                                " input shape=" + shapeToString(in) +
                                ", output shape=" + shapeToString(out) +
                                ", outputClasses=" + outClasses +
                                ", labelMapSize=" + mapSize);
            } catch (Exception e) {
                Log.e(TAG, "Failed to log stage2 tensor shape for " + shapeName(sc), e);
            }
        }
    }

    private String shapeToString(int[] shape) {
        if (shape == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < shape.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(shape[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private MappedByteBuffer loadModelFile(Context context, String modelFile) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFile);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private HashMap<Integer, String> loadLabelMap(Context context, String jsonFile) throws IOException {
        InputStream is = context.getAssets().open(jsonFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();

        HashMap<Integer, String> map = new HashMap<>();
        try {
            JSONObject root = new JSONObject(sb.toString());
            JSONObject lut = root.getJSONObject("local_to_item_seq");

            java.util.Iterator<String> keys = lut.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(Integer.parseInt(key), lut.getString(key));
            }

            Log.d(TAG, "Loaded " + jsonFile + " labelmap size=" + map.size());
        } catch (Exception e) {
            Log.e(TAG, "JSON parse failed: " + jsonFile, e);
        }

        return map;
    }

    // main inference
    public InferenceResult classify(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "classify: bitmap is null");
            return new InferenceResult(
                    new Stage1Result(new float[]{0f, 0f, 0f, 0f}, -1, -1, 0f, 0f, false),
                    Collections.emptyList()
            );
        }

        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        if (bitmap == null) {
            Log.e(TAG, "classify: bitmap.copy failed");
            return new InferenceResult(
                    new Stage1Result(new float[]{0f, 0f, 0f, 0f}, -1, -1, 0f, 0f, false),
                    Collections.emptyList()
            );
        }

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        resized = resized.copy(Bitmap.Config.ARGB_8888, false);
        if (resized == null) {
            Log.e(TAG, "classify: resized.copy failed");
            return new InferenceResult(
                    new Stage1Result(new float[]{0f, 0f, 0f, 0f}, -1, -1, 0f, 0f, false),
                    Collections.emptyList()
            );
        }

        // Stage1
        ByteBuffer stage1Input = bitmapToMobileNetV3Input(resized);
        float[][] stage1Output = new float[1][NUM_SHAPES];
        stage1Interpreter.run(stage1Input, stage1Output);

        float[] shapeProbs = ensureProbabilities(stage1Output[0]);
        int[] top2 = getTopKIndices(shapeProbs, 2);

        int top1Class = top2.length > 0 ? top2[0] : -1;
        int top2Class = top2.length > 1 ? top2[1] : -1;
        float top1Prob = top1Class >= 0 ? shapeProbs[top1Class] : 0f;
        float secondProb = top2Class >= 0 ? shapeProbs[top2Class] : 0f;

        boolean runOtherAlso =
                (top1Prob <= STAGE1_PROB_THRESHOLD) ||
                        ((top1Prob - secondProb) <= STAGE1_GAP_THRESHOLD);

        Log.d(TAG,
                "Stage1 probs => circle=" + shapeProbs[0] +
                        ", oval=" + shapeProbs[1] +
                        ", oblong=" + shapeProbs[2] +
                        ", other=" + shapeProbs[3]);

        Log.d(TAG,
                "Stage1 route => top1=" + shapeName(top1Class) + "(" + top1Class + ")" +
                        ", top2=" + shapeName(top2Class) + "(" + top2Class + ")" +
                        ", top1Prob=" + top1Prob +
                        ", top2Prob=" + secondProb +
                        ", gap=" + (top1Prob - secondProb) +
                        ", runOtherAlso=" + runOtherAlso);

        Stage1Result stage1Result = new Stage1Result(
                shapeProbs, top1Class, top2Class, top1Prob, secondProb, runOtherAlso
        );

        // Stage2 merge
        List<Prediction> mergedTop5 = runMergedStage2(resized, stage1Result);

        for (int i = 0; i < mergedTop5.size(); i++) {
            Prediction p = mergedTop5.get(i);
            Log.d(TAG, "Final Top" + (i + 1) + " => itemSeq=" + p.label + ", prob=" + p.score + ", source=" + p.source + ", localIdx=" + p.classIndex);
        }

        return new InferenceResult(stage1Result, mergedTop5);
    }

    // typo compatibility
    public InferenceResult clssify(Bitmap bitmap) {
        return classify(bitmap);
    }

    private ByteBuffer bitmapToMobileNetV3Input(Bitmap bitmap) {
        // IMPORTANT:
        // mobilenet_v3.preprocess_input is pass-through by default,
        // and MobileNetV3 expects float pixels in [0,255] if preprocessing is included in model.
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        inputBuffer.order(ByteOrder.nativeOrder());

        int idx = 0;
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = pixels[idx++];

                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // pass 0..255 float
                inputBuffer.putFloat((float) r);
                inputBuffer.putFloat((float) g);
                inputBuffer.putFloat((float) b);
            }
        }

        inputBuffer.rewind();
        return inputBuffer;
    }

    private List<Prediction> runMergedStage2(Bitmap resizedBitmap, Stage1Result stage1Result) {
        List<Prediction> candidates = new ArrayList<>();

        if (stage1Result.top1Class >= 0) {
            candidates.addAll(runSingleStage2(resizedBitmap, stage1Result.top1Class));
        }

        int OTHER_CLASS = 3;
        if (stage1Result.runOtherAlso && stage1Result.top1Class != OTHER_CLASS) {
            candidates.addAll(runSingleStage2(resizedBitmap, OTHER_CLASS));
        }

        HashMap<String, Prediction> bestByItemSeq = new HashMap<>();
        for (Prediction p : candidates) {
            Prediction prev = bestByItemSeq.get(p.label);
            if (prev == null || p.score > prev.score) {
                bestByItemSeq.put(p.label, p);
            }
        }

        List<Prediction> merged = new ArrayList<>(bestByItemSeq.values());
        Collections.sort(merged, (a, b) -> Float.compare(b.score, a.score));

        if (merged.size() > 5) {
            return new ArrayList<>(merged.subList(0, 5));
        }
        return merged;
    }

    private List<Prediction> runSingleStage2(Bitmap bitmap, int shapeClass) {
        Interpreter interpreter = stage2Interpreters.get(shapeClass);
        HashMap<Integer, String> labelMap = stage2LabelMaps.get(shapeClass);

        if (interpreter == null || labelMap == null) {
            Log.e(TAG, "runSingleStage2: missing interpreter or labelmap for shapeClass=" + shapeClass);
            return Collections.emptyList();
        }

        int outputClasses;
        try {
            int[] outShape = interpreter.getOutputTensor(0).shape();
            outputClasses = outShape[outShape.length - 1];
        } catch (Exception e) {
            Log.e(TAG, "runSingleStage2: failed to get output tensor shape for " + shapeName(shapeClass), e);
            outputClasses = labelMap.size();
        }

        if (outputClasses <= 0) {
            Log.e(TAG, "runSingleStage2: invalid output size for shapeClass=" + shapeClass);
            return Collections.emptyList();
        }

        if (labelMap.size() != outputClasses) {
            Log.w(TAG, "LabelMap size != model output classes for " + shapeName(shapeClass)
                    + " | labelMap=" + labelMap.size() + ", output=" + outputClasses);
        }

        ByteBuffer inputBuffer = bitmapToMobileNetV3Input(bitmap);
        float[][] output = new float[1][outputClasses];
        interpreter.run(inputBuffer, output);

        float[] probs = ensureProbabilities(output[0]);
        List<Integer> topIdx = getTopKIndicesList(probs, 5);

        List<Prediction> result = new ArrayList<>();
        for (int idx : topIdx) {
            String itemSeq = labelMap.get(idx);
            if (itemSeq == null) {
                itemSeq = "local_" + idx;
                Log.w(TAG, "Missing labelMap entry: shape=" + shapeName(shapeClass) + ", localIdx=" + idx);
            }
            result.add(new Prediction(idx, itemSeq, probs[idx], shapeName(shapeClass)));
        }

        Log.d(TAG, "Stage2 " + shapeName(shapeClass) + " top results:");
        for (Prediction p : result) {
            Log.d(TAG, "  localIdx=" + p.classIndex + ", itemSeq=" + p.label + ", prob=" + p.score);
        }

        return result;
    }

    private float[] ensureProbabilities(float[] scores) {
        if (scores == null || scores.length == 0) return new float[0];

        boolean allNonNegative = true;
        float sum = 0f;
        for (float v : scores) {
            if (v < 0f) allNonNegative = false;
            sum += v;
        }

        if (allNonNegative && sum > 0.9f && sum < 1.1f) {
            return scores;
        }

        float max = -Float.MAX_VALUE;
        for (float v : scores) {
            if (v > max) max = v;
        }

        float expSum = 0f;
        float[] exps = new float[scores.length];
        for (int i = 0; i < scores.length; i++) {
            exps[i] = (float) Math.exp(scores[i] - max);
            expSum += exps[i];
        }

        float[] probs = new float[scores.length];
        if (expSum == 0f) return probs;

        for (int i = 0; i < scores.length; i++) {
            probs[i] = exps[i] / expSum;
        }
        return probs;
    }

    private int[] getTopKIndices(float[] scores, int k) {
        List<Integer> list = getTopKIndicesList(scores, k);
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private List<Integer> getTopKIndicesList(float[] scores, int k) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            indices.add(i);
        }

        Collections.sort(indices, (a, b) -> Float.compare(scores[b], scores[a]));

        if (indices.size() > k) {
            return new ArrayList<>(indices.subList(0, k));
        }
        return indices;
    }

    public static String shapeName(int shapeClass) {
        switch (shapeClass) {
            case 0: return "circle";
            case 1: return "oval";
            case 2: return "oblong";
            case 3: return "other";
            default: return "unknown";
        }
    }

    public void close() {
        try { stage1Interpreter.close(); } catch (Exception ignored) {}

        for (Interpreter it : stage2Interpreters.values()) {
            try { it.close(); } catch (Exception ignored) {}
        }
    }
}