package com.sookmyung.medicell;

public class NativeBridge {
    static {
        System.loadLibrary("medicell_native");
    }

    /**
     * @param rgba  width*height*4 (RGBA_8888, rowStride 정리된 연속 배열)
     * @param outMetrics 길이 3 이상이면 [0]=areaRatio, [1]=centerOffsetNorm, [2]=edgeDensity 채움
     * @return 1이면 OK(초록), 0이면 NOT OK(빨강)
     */
    public static native int analyzeRoi(byte[] rgba, int width, int height, int radius, float[] outMetrics);

    // ABI 확인용(선택)
    public static String abis() {
        StringBuilder sb = new StringBuilder();
        for (String a : android.os.Build.SUPPORTED_ABIS) sb.append(a).append(" ");
        return sb.toString().trim();
    }
}
