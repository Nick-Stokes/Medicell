#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <vector>

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MedicellROI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MedicellROI", __VA_ARGS__)

extern "C"
JNIEXPORT jint JNICALL
Java_com_sookmyung_medicell_NativeBridge_analyzeRoi(JNIEnv* env, jclass,
                                                    jbyteArray rgba_,
                                                    jint width, jint height,
                                                    jint radius,
                                                    jfloatArray outMetrics_) {
    if (rgba_ == nullptr) return 0;
    if (width <= 0 || height <= 0 || radius <= 5) return 0;

    jsize n = env->GetArrayLength(rgba_);
    const int expected = width * height * 4;
    if (n < expected) {
        LOGE("rgba length too small: %d < %d", (int)n, expected);
        return 0;
    }

    jboolean isCopy = JNI_FALSE;
    jbyte* rgbaPtr = env->GetByteArrayElements(rgba_, &isCopy);
    if (!rgbaPtr) return 0;

    cv::Mat rgba(height, width, CV_8UC4, (unsigned char*)rgbaPtr);

    const cv::Point2f c((float)width / 2.f, (float)height / 2.f);
    const float r = (float)radius;
    const float roiArea = (float)(CV_PI * r * r);

    // ── 1) ROI 마스크 ──────────────────────────────────
    cv::Mat mask(height, width, CV_8UC1, cv::Scalar(0));
    cv::circle(mask, c, radius, cv::Scalar(255), cv::FILLED);

    // ── 2) RGB → Gray + Blur ──────────────────────────
    cv::Mat gray;
    cv::cvtColor(rgba, gray, cv::COLOR_RGBA2GRAY);
    cv::GaussianBlur(gray, gray, cv::Size(5, 5), 0);

    // ── 3) 배경 밝기 추정 (코너 4곳 평균) ──────────────
    // 흰 배경 가정: 코너 픽셀들의 평균 = 배경 밝기
    int cornerSize = std::max(3, (int)(width * 0.05f));
    cv::Rect tl(0, 0, cornerSize, cornerSize);
    cv::Rect tr(width - cornerSize, 0, cornerSize, cornerSize);
    cv::Rect bl(0, height - cornerSize, cornerSize, cornerSize);
    cv::Rect br(width - cornerSize, height - cornerSize, cornerSize, cornerSize);

    double bgMean = (
                            cv::mean(gray(tl))[0] +
                            cv::mean(gray(tr))[0] +
                            cv::mean(gray(bl))[0] +
                            cv::mean(gray(br))[0]) / 4.0;

    // ── 4) 배경과 차이나는 픽셀 = 알약 영역 ────────────
    // 배경보다 어두운 픽셀 (알약은 배경보다 어둡거나 색이 다름)
    float diffThreshold = 20.f;
    cv::Mat diffMask;
    cv::absdiff(gray, cv::Scalar(bgMean), diffMask);
    cv::threshold(diffMask, diffMask, diffThreshold, 255, cv::THRESH_BINARY);

    // 노이즈 제거
    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_ELLIPSE,
                                               cv::Size(3, 3));
    cv::morphologyEx(diffMask, diffMask, cv::MORPH_OPEN, kernel);
    cv::morphologyEx(diffMask, diffMask, cv::MORPH_CLOSE, kernel);

    // ── 5) ROI 안에서만 분석 ──────────────────────────
    cv::Mat maskEroded;
    cv::erode(mask, maskEroded, cv::Mat(), cv::Point(-1, -1), 2);

    cv::Mat roiDiff;
    cv::bitwise_and(diffMask, maskEroded, roiDiff);

    int pillPixels = cv::countNonZero(roiDiff);
    float areaRatio = (roiArea > 1.f) ? ((float)pillPixels / roiArea) : 0.f;

    // ── 6) 알약 중심 계산 ─────────────────────────────
    float centerOffsetNorm = 1.f;
    if (pillPixels > 10) {
        cv::Moments m = cv::moments(roiDiff);
        if (std::abs(m.m00) > 1e-5) {
            cv::Point2f pc((float)(m.m10 / m.m00),
                           (float)(m.m01 / m.m00));
            float dist = (float)cv::norm(pc - c);
            centerOffsetNorm = dist / r;
        }
    }

    // ── 7) Canny 엣지 (선명도 체크용) ─────────────────
    cv::Mat edgesFull;
    cv::Canny(gray, edgesFull, 30, 90);
    cv::Mat edges;
    cv::bitwise_and(edgesFull, maskEroded, edges);
    int edgeCount = cv::countNonZero(edges);
    float edgeDensity = (roiArea > 1.f) ? (edgeCount / roiArea) : 0.f;

    // ── 8) 판정 ───────────────────────────────────────
    // areaRatio: 알약이 ROI의 몇 % 차지하는지
    // centerOffsetNorm: 알약 중심이 가이드 원 중심에서 얼마나 벗어났는지
    // edgeDensity: 엣지가 충분한지 (초점 흐림 감지)
    const float AREA_MIN    = 0.05f;   // ROI의 5% 이상
    const float AREA_MAX    = 0.98f;   // ROI의 98% 이하
    const float CENTER_MAX  = 0.50f;   // 중심 오프셋 50% 이하
    const float EDGE_MIN    = 0.001f;  // 엣지 최소 (초점)

    bool ok = (areaRatio    >= AREA_MIN  &&
               areaRatio    <= AREA_MAX  &&
               centerOffsetNorm <= CENTER_MAX &&
               edgeDensity  >= EDGE_MIN);

    LOGI("area=%.3f center=%.3f edge=%.4f bg=%.0f ok=%d",
         areaRatio, centerOffsetNorm, edgeDensity, bgMean, ok ? 1 : 0);

    // ── 9) 메트릭 출력 ────────────────────────────────
    if (outMetrics_ != nullptr) {
        jsize mlen = env->GetArrayLength(outMetrics_);
        if (mlen >= 3) {
            jfloat vals[3] = {areaRatio, centerOffsetNorm, edgeDensity};
            env->SetFloatArrayRegion(outMetrics_, 0, 3, vals);
        }
    }

    env->ReleaseByteArrayElements(rgba_, rgbaPtr, JNI_ABORT);
    return ok ? 1 : 0;
}