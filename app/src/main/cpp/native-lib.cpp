#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <vector>

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MedicellROI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MedicellROI", __VA_ARGS__)

static inline float clamp01(float x) {
    if (x < 0.f) return 0.f;
    if (x > 1.f) return 1.f;
    return x;
}

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

    // RGBA -> Mat (CV_8UC4)
    cv::Mat rgba(height, width, CV_8UC4, (unsigned char*)rgbaPtr);

    const cv::Point2f c((float)width / 2.f, (float)height / 2.f);
    const float r = (float)radius;
    const float roiArea = (float)(CV_PI * r * r);

    // 1) ROI 마스크(원)
    cv::Mat mask(height, width, CV_8UC1, cv::Scalar(0));
    cv::circle(mask, c, radius, cv::Scalar(255), cv::FILLED);

    // 2) Gray + Blur
    cv::Mat gray;
    cv::cvtColor(rgba, gray, cv::COLOR_RGBA2GRAY);
    cv::GaussianBlur(gray, gray, cv::Size(5,5), 0);

// 3) Canny는 전체에서 수행
    cv::Mat edgesFull;
    cv::Canny(gray, edgesFull, 40, 120);

// 4) 마스크 경계 제거를 위해 mask를 조금 깎고(erode) ROI 내부만 남기기
    cv::Mat maskE;
    cv::erode(mask, maskE, cv::Mat(), cv::Point(-1,-1), 2); // 2는 시작값, 필요하면 1~3 조정

    cv::Mat edges;
    cv::bitwise_and(edgesFull, maskE, edges);

// edgeDensity (ROI 내부 edge만)
    int edgeCount = cv::countNonZero(edges);
    float edgeDensity = (roiArea > 1.f) ? (edgeCount / roiArea) : 0.f;

// 5) 컨투어
    cv::Mat edgesDil;
    cv::dilate(edges, edgesDil, cv::Mat(), cv::Point(-1,-1), 1);

    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(edgesDil, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);


    float bestArea = 0.f;
    int bestIdx = -1;

    for (int i = 0; i < (int)contours.size(); i++) {
        float a = (float)cv::contourArea(contours[i]);
        if (a > bestArea) {
            bestArea = a;
            bestIdx = i;
        }
    }

    float areaRatio = 0.f;
    float centerOffsetNorm = 1.f;

    bool ok = false;

    if (bestIdx >= 0 && bestArea > 5.f) {
        areaRatio = bestArea / roiArea;

        cv::Moments m = cv::moments(contours[bestIdx]);
        if (std::abs(m.m00) > 1e-5) {
            cv::Point2f pc((float)(m.m10 / m.m00), (float)(m.m01 / m.m00));
            float dist = cv::norm(pc - c);
            centerOffsetNorm = dist / r;
        } else {
            centerOffsetNorm = 1.f;
        }

        // ✅ 초기 튜닝값 (너 샘플 기준으로 "넉넉하게" 시작)
        // - areaRatio: 너무 작으면 멀거나 없음, 너무 크면 너무 가깝거나 오탐
        // - centerOffsetNorm: 중심에서 너무 벗어나면 실패
        // - edgeDensity: 경계가 거의 없으면 실패(배경만)
        const float AREA_MIN = 0.004f;
        const float AREA_MAX = 0.30f;
        const float CENTER_MAX = 0.35f;
        const float EDGE_MIN = 0.0020f;

        ok = (areaRatio >= AREA_MIN && areaRatio <= AREA_MAX &&
              centerOffsetNorm <= CENTER_MAX &&
              edgeDensity >= EDGE_MIN);
    } else {
        // 컨투어 자체가 없으면 거의 배경만
        ok = false;
        areaRatio = 0.f;
        centerOffsetNorm = 1.f;
    }

    // outMetrics 채우기(선택)
    if (outMetrics_ != nullptr) {
        jsize mlen = env->GetArrayLength(outMetrics_);
        if (mlen >= 3) {
            jfloat vals[3];
            vals[0] = areaRatio;
            vals[1] = centerOffsetNorm;
            vals[2] = edgeDensity; // edgeDensity는 0~대략 0.1대라 clamp 안 함
            env->SetFloatArrayRegion(outMetrics_, 0, 3, vals);
        }
    }

    env->ReleaseByteArrayElements(rgba_, rgbaPtr, JNI_ABORT);
    return ok ? 1 : 0;
}
