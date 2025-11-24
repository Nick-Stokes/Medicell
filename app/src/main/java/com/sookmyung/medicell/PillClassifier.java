package com.sookmyung.medicell;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

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
import java.util.List;

public class PillClassifier {

    private static final String TAG = "PillClassifier";
    private static final String MODEL_FILE = "pill_classifier.tflite";
    private static final String LABEL_FILE = "pill_labels.txt";
    private static final int INPUT_SIZE = 224;

    private final Interpreter interpreter;
    private final List<String> labels;

    public static class Prediction {
        public final int classIndex;
        public final String label;   // 여기에 itemSeq (품목기준코드) 들어감
        public final float score;

        public Prediction(int classIndex, String label, float score) {
            this.classIndex = classIndex;
            this.label = label;
            this.score = score;
        }
    }

    public PillClassifier(Context context) throws IOException {
        Log.d(TAG, "PillClassifier 생성자 시작");
        MappedByteBuffer modelBuffer = loadModelFile(context);
        interpreter = new Interpreter(modelBuffer);
        Log.d(TAG, "모델 로드 성공: " + MODEL_FILE);

        labels = loadLabels(context);
        Log.d(TAG, "레이블 개수 = " + labels.size());
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabels(Context context) throws IOException {
        List<String> labelList = new ArrayList<>();
        InputStream is = context.getAssets().open(LABEL_FILE);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
                labelList.add(line);  // 각 줄이 itemSeq (예: 196000001)
            }
        }
        br.close();
        return labelList;
    }

    // 새 메서드 이름 (추천)
    public List<Prediction> classify(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "classify: bitmap 이 null 입니다.");
            return Collections.emptyList();
        }

        // 하드웨어 비트맵 → 일반 비트맵
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        if (bitmap == null) {
            Log.e(TAG, "classify: bitmap.copy 실패");
            return Collections.emptyList();
        }

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        resized = resized.copy(Bitmap.Config.ARGB_8888, false);
        if (resized == null) {
            Log.e(TAG, "classify: resized.copy 실패");
            return Collections.emptyList();
        }

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        inputBuffer.order(ByteOrder.nativeOrder());

        int idx = 0;
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = pixels[idx++];

                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                float rf = r / 255.0f;
                float gf = g / 255.0f;
                float bf = b / 255.0f;

                inputBuffer.putFloat(rf);
                inputBuffer.putFloat(gf);
                inputBuffer.putFloat(bf);
            }
        }

        int numClasses = labels.size();
        float[][] output = new float[1][numClasses];

        // ★ 여기서 실제 추론 수행
        interpreter.run(inputBuffer, output);

        return getTopK(output[0], 5);
    }

    // 이전에 쓰던 오타 함수도 유지 (호환용)
    public List<Prediction> clssify(Bitmap bitmap) {
        return classify(bitmap);
    }

    private List<Prediction> getTopK(float[] scores, int k) {
        List<Prediction> result = new ArrayList<>();
        int n = scores.length;
        boolean[] used = new boolean[n];

        for (int i = 0; i < k; i++) {
            int bestIndex = -1;
            float bestScore = -Float.MAX_VALUE;

            for (int j = 0; j < n; j++) {
                if (!used[j] && scores[j] > bestScore) {
                    bestScore = scores[j];
                    bestIndex = j;
                }
            }

            if (bestIndex == -1) break;
            used[bestIndex] = true;

            String label;
            if (bestIndex >= 0 && bestIndex < labels.size()) {
                label = labels.get(bestIndex); // 여기서 label = itemSeq
            } else {
                label = "class_" + bestIndex;
            }

            result.add(new Prediction(bestIndex, label, bestScore));
        }

        return result;
    }

    public void close() {
        interpreter.close();
    }
}
