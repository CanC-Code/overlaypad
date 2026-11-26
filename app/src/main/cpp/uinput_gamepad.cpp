#include <jni.h>
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <cerrno>
#include <sys/ioctl.h>
#include <linux/input.h>
#include <linux/uinput.h>

#define LOG_TAG "uinputpad"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

int g_uinput_fd = -1;

bool emit_event(int fd, uint16_t type, uint16_t code, int32_t value) {
    if (fd < 0) return false;
    struct input_event ev;
    std::memset(&ev, 0, sizeof(ev));
    ev.type = type;
    ev.code = code;
    ev.value = value;
    ssize_t written = write(fd, &ev, sizeof(ev));
    return written == static_cast<ssize_t>(sizeof(ev));
}

bool emit_syn(int fd) {
    return emit_event(fd, EV_SYN, SYN_REPORT, 0);
}

void setup_abs(int fd, int axis, int min_val, int max_val, int fuzz, int flat) {
    ioctl(fd, UI_SET_ABSBIT, axis);
    struct uinput_abs_setup abs_setup;
    std::memset(&abs_setup, 0, sizeof(abs_setup));
    abs_setup.code = static_cast<uint16_t>(axis);
    abs_setup.absinfo.minimum = min_val;
    abs_setup.absinfo.maximum = max_val;
    abs_setup.absinfo.fuzz = fuzz;
    abs_setup.absinfo.flat = flat;
    ioctl(fd, UI_ABS_SETUP, &abs_setup);
}

void close_fd_locked() {
    if (g_uinput_fd >= 0) {
        ioctl(g_uinput_fd, UI_DEV_DESTROY);
        close(g_uinput_fd);
        g_uinput_fd = -1;
    }
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_overlaypad_GamepadUserService_nativeInitGamepad(
        JNIEnv* env,
        jobject /* thiz */,
        jstring j_name,
        jint vendor_id,
        jint product_id) {
    close_fd_locked();

    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        LOGE("Failed to open /dev/uinput: errno=%d (%s)", errno, std::strerror(errno));
        return JNI_FALSE;
    }

    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0 ||
        ioctl(fd, UI_SET_EVBIT, EV_ABS) < 0 ||
        ioctl(fd, UI_SET_EVBIT, EV_SYN) < 0) {
        LOGE("Failed setting event bits: errno=%d", errno);
        close(fd);
        return JNI_FALSE;
    }

    const int buttons[] = {
        BTN_SOUTH, BTN_EAST, BTN_NORTH, BTN_WEST,
        BTN_TL, BTN_TR, BTN_TL2, BTN_TR2,
        BTN_SELECT, BTN_START, BTN_MODE,
        BTN_THUMBL, BTN_THUMBR,
        BTN_DPAD_UP, BTN_DPAD_DOWN, BTN_DPAD_LEFT, BTN_DPAD_RIGHT
    };
    for (int btn : buttons) {
        ioctl(fd, UI_SET_KEYBIT, btn);
    }

    setup_abs(fd, ABS_X,  -32768, 32767, 16, 128);
    setup_abs(fd, ABS_Y,  -32768, 32767, 16, 128);
    setup_abs(fd, ABS_RX, -32768, 32767, 16, 128);
    setup_abs(fd, ABS_RY, -32768, 32767, 16, 128);
    setup_abs(fd, ABS_Z,       0,   255,  0,   0);
    setup_abs(fd, ABS_RZ,      0,   255,  0,   0);

    struct uinput_setup usetup;
    std::memset(&usetup, 0, sizeof(usetup));
    usetup.id.bustype = BUS_BLUETOOTH;
    usetup.id.vendor  = static_cast<uint16_t>(vendor_id);
    usetup.id.product = static_cast<uint16_t>(product_id);
    usetup.id.version = 0x0100;

    const char* raw_name = env->GetStringUTFChars(j_name, nullptr);
    if (raw_name != nullptr) {
        std::strncpy(usetup.name, raw_name, UINPUT_MAX_NAME_SIZE - 1);
        env->ReleaseStringUTFChars(j_name, raw_name);
    } else {
        std::strncpy(usetup.name, "OverlayPad Gamepad", UINPUT_MAX_NAME_SIZE - 1);
    }

    if (ioctl(fd, UI_DEV_SETUP, &usetup) < 0) {
        LOGE("UI_DEV_SETUP failed: errno=%d (%s)", errno, std::strerror(errno));
        close(fd);
        return JNI_FALSE;
    }

    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        LOGE("UI_DEV_CREATE failed: errno=%d (%s)", errno, std::strerror(errno));
        close(fd);
        return JNI_FALSE;
    }

    g_uinput_fd = fd;
    LOGI("Created virtual uinput gamepad: %s", usetup.name);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_overlaypad_GamepadUserService_nativeSetButton(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint linux_key_code,
        jboolean pressed) {
    if (g_uinput_fd < 0) return;
    if (emit_event(g_uinput_fd, EV_KEY, static_cast<uint16_t>(linux_key_code), pressed ? 1 : 0)) {
        emit_syn(g_uinput_fd);
    }
}

JNIEXPORT void JNICALL
Java_com_overlaypad_GamepadUserService_nativeSetAbsAxis(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jint linux_abs_code,
        jint value) {
    if (g_uinput_fd < 0) return;
    if (emit_event(g_uinput_fd, EV_ABS, static_cast<uint16_t>(linux_abs_code), value)) {
        emit_syn(g_uinput_fd);
    }
}

JNIEXPORT void JNICALL
Java_com_overlaypad_GamepadUserService_nativeCloseGamepad(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    close_fd_locked();
}

} // extern "C"
