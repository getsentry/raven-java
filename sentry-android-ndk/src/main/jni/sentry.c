#include <string.h>
#include <stdbool.h>
#include <sentry.h>
#include <jni.h>

#define ENSURE(Expr) \
    if (!(Expr))     \
        return

#define ENSURE_OR_FAIL(Expr) \
    if (!(Expr))          \
        goto fail

static bool get_string_into(JNIEnv *env, jstring jstr, char* buf, size_t buf_len)
{
    jsize utf_len = (*env)->GetStringUTFLength(env, jstr);
    if ((size_t)utf_len >= buf_len) {
        return false;
    }

    jsize j_len = (*env)->GetStringLength(env, jstr);

    (*env)->GetStringUTFRegion(env, jstr, 0, j_len, buf);
    if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
        return false;
    }

    buf[utf_len] = '\0';
    return true;
}

static char* get_string(JNIEnv *env, jstring jstr) {
    char *buf = NULL;

    jsize utf_len = (*env)->GetStringUTFLength(env, jstr);
    size_t buf_len = (size_t)utf_len + 1;
    buf = sentry_malloc(buf_len);
    ENSURE_OR_FAIL(buf);

    ENSURE_OR_FAIL(get_string_into(env, jstr, buf, buf_len));

    return buf;

fail:
    sentry_free(buf);

    return NULL;
}

static char *call_get_string(JNIEnv *env, jobject obj, jmethodID mid)
{
    jstring j_str = (jstring)(*env)->CallObjectMethod(env, obj, mid);
    ENSURE_OR_FAIL(j_str);
    char* str = get_string(env, j_str);
    (*env)->DeleteLocalRef(env, j_str);

    return str;

fail:
    return NULL;
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeSetTag(
        JNIEnv *env,
        jclass cls,
        jstring key,
        jstring value) {
    const char *charKey = (*env)->GetStringUTFChars(env, key, 0);
    const char *charValue = (*env)->GetStringUTFChars(env, value, 0);

    sentry_set_tag(charKey, charValue);

    (*env)->ReleaseStringUTFChars(env, key, charKey);
    (*env)->ReleaseStringUTFChars(env, value, charValue);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeRemoveTag(JNIEnv *env, jclass cls, jstring key) {
    const char *charKey = (*env)->GetStringUTFChars(env, key, 0);

    sentry_remove_tag(charKey);

    (*env)->ReleaseStringUTFChars(env, key, charKey);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeSetExtra(
        JNIEnv *env,
        jclass cls,
        jstring key,
        jstring value) {
    const char *charKey = (*env)->GetStringUTFChars(env, key, 0);
    const char *charValue = (*env)->GetStringUTFChars(env, value, 0);

    sentry_value_t sentryValue = sentry_value_new_string(charValue);
    sentry_set_extra(charKey, sentryValue);

    (*env)->ReleaseStringUTFChars(env, key, charKey);
    (*env)->ReleaseStringUTFChars(env, value, charValue);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeRemoveExtra(JNIEnv *env, jclass cls, jstring key) {
    const char *charKey = (*env)->GetStringUTFChars(env, key, 0);

    sentry_remove_extra(charKey);

    (*env)->ReleaseStringUTFChars(env, key, charKey);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeSetUser(
        JNIEnv *env,
        jclass cls,
        jstring id,
        jstring email,
        jstring ipAddress,
        jstring username) {
    sentry_value_t user = sentry_value_new_object();
    if (id) {
        const char *charId = (*env)->GetStringUTFChars(env, id, 0);
        sentry_value_set_by_key(user, "id", sentry_value_new_string(charId));
        (*env)->ReleaseStringUTFChars(env, id, charId);
    }
    if (email) {
        const char *charEmail = (*env)->GetStringUTFChars(env, email, 0);
        sentry_value_set_by_key(
                user, "email", sentry_value_new_string(charEmail));
        (*env)->ReleaseStringUTFChars(env, email, charEmail);
    }
    if (ipAddress) {
        const char *charIpAddress = (*env)->GetStringUTFChars(env, ipAddress, 0);
        sentry_value_set_by_key(
                user, "ip_address", sentry_value_new_string(charIpAddress));
        (*env)->ReleaseStringUTFChars(env, ipAddress, charIpAddress);
    }
    if (username) {
        const char *charUsername = (*env)->GetStringUTFChars(env, username, 0);
        sentry_value_set_by_key(
                user, "username", sentry_value_new_string(charUsername));
        (*env)->ReleaseStringUTFChars(env, username, charUsername);
    }
    sentry_set_user(user);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeRemoveUser(JNIEnv *env, jclass cls) {
    sentry_remove_user();
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeScope_nativeAddBreadcrumb(
        JNIEnv *env,
        jclass cls,
        jstring level,
        jstring message,
        jstring category,
        jstring type,
        jstring timestamp,
        jstring data) {
    if (!level && !message && !category && !type) {
        return;
    }
    const char *charMessage = NULL;
    if (message) {
        charMessage = (*env)->GetStringUTFChars(env, message, 0);
    }
    const char *charType = NULL;
    if (type) {
        charType = (*env)->GetStringUTFChars(env, type, 0);
    }
    sentry_value_t crumb = sentry_value_new_breadcrumb(charType, charMessage);

    if (charMessage) {
        (*env)->ReleaseStringUTFChars(env, message, charMessage);
    }
    if (charType) {
        (*env)->ReleaseStringUTFChars(env, type, charType);
    }

    if (category) {
        const char *charCategory = (*env)->GetStringUTFChars(env, category, 0);
        sentry_value_set_by_key(
                crumb, "category", sentry_value_new_string(charCategory));
        (*env)->ReleaseStringUTFChars(env, category, charCategory);
    }
    if (level) {
        const char *charLevel = (*env)->GetStringUTFChars(env, level, 0);
        sentry_value_set_by_key(
                crumb, "level", sentry_value_new_string(charLevel));
        (*env)->ReleaseStringUTFChars(env, level, charLevel);
    }

    if (timestamp) {
        // overwrite timestamp that is already created on sentry_value_new_breadcrumb
        const char *charTimestamp = (*env)->GetStringUTFChars(env, timestamp, 0);
        sentry_value_set_by_key(
                crumb, "timestamp", sentry_value_new_string(charTimestamp));
        (*env)->ReleaseStringUTFChars(env, timestamp, charTimestamp);
    }

    if (data) {
        const char *charData = (*env)->GetStringUTFChars(env, data, 0);

        // we create an object because the Java layer parses it as a Map
        sentry_value_t dataObject = sentry_value_new_object();
        sentry_value_set_by_key(dataObject, "data", sentry_value_new_string(charData));

        sentry_value_set_by_key(crumb, "data", dataObject);

        (*env)->ReleaseStringUTFChars(env, data, charData);
    }

    sentry_add_breadcrumb(crumb);
}

static void send_envelope(sentry_envelope_t *envelope, void *data) {
    const char *outbox_path = (const char *) data;
    char envelope_id_str[40];
    char envelope_path[4096];

    sentry_uuid_t envelope_id = sentry_uuid_new_v4();
    sentry_uuid_as_string(&envelope_id, envelope_id_str);

    strcpy(envelope_path, outbox_path);
    strcat(envelope_path, "/");
    strcat(envelope_path, envelope_id_str);

    sentry_envelope_write_to_file(envelope, envelope_path);

    sentry_envelope_free(envelope);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_SentryNdk_initSentryNative(
        JNIEnv *env,
        jclass cls,
        jobject sentry_sdk_options) {
    jclass options_cls = (*env)->GetObjectClass(env, sentry_sdk_options);
    jmethodID outbox_path_mid = (*env)->GetMethodID(env, options_cls, "getOutboxPath",
                                                    "()Ljava/lang/String;");
    jmethodID dsn_mid = (*env)->GetMethodID(env, options_cls, "getDsn", "()Ljava/lang/String;");
    jmethodID is_debug_mid = (*env)->GetMethodID(env, options_cls, "isDebug", "()Z");
    jmethodID release_mid = (*env)->GetMethodID(env, options_cls, "getRelease",
                                                "()Ljava/lang/String;");
    jmethodID environment_mid = (*env)->GetMethodID(env, options_cls, "getEnvironment",
                                                    "()Ljava/lang/String;");
    jmethodID dist_mid = (*env)->GetMethodID(env, options_cls, "getDist", "()Ljava/lang/String;");
    jmethodID max_crumbs_mid = (*env)->GetMethodID(env, options_cls, "getMaxBreadcrumbs", "()I");

    (*env)->DeleteLocalRef(env, options_cls);

    char *outbox_path = NULL;
    sentry_transport_t *transport = NULL;
    sentry_options_t *options = NULL;
    char *dsn_str = NULL;
    char *release_str = NULL;
    char *environment_str = NULL;
    char *dist_str = NULL;

    options = sentry_options_new();
    ENSURE_OR_FAIL(options);

    // session tracking is enabled by default, but the Android SDK already handles it
    sentry_options_set_auto_session_tracking(options, 0);

    jboolean debug = (jboolean)(*env)->CallBooleanMethod(env, sentry_sdk_options, is_debug_mid);
    sentry_options_set_debug(options, debug);

    jint max_crumbs = (jint) (*env)->CallIntMethod(env, sentry_sdk_options, max_crumbs_mid);
    sentry_options_set_max_breadcrumbs(options, max_crumbs);

    outbox_path = call_get_string(env, sentry_sdk_options, outbox_path_mid);
    ENSURE_OR_FAIL(outbox_path);

    transport = sentry_transport_new(send_envelope);
    ENSURE_OR_FAIL(transport);
    sentry_transport_set_state(transport, outbox_path);
    sentry_transport_set_free_func(transport, sentry_free);

    sentry_options_set_transport(options, transport);

    // give sentry-native its own database path it can work with, next to the outbox
    char database_path[4096];
    strncpy(database_path, outbox_path, 4096);
    char *dir = strrchr(database_path, '/');
    if (dir)
    {
        strncpy(dir + 1, ".sentry-native", 4096 - (dir + 1 - database_path));
    }
    sentry_options_set_database_path(options, database_path);

    dsn_str = call_get_string(env, sentry_sdk_options, dsn_mid);
    ENSURE_OR_FAIL(dsn_str);
    sentry_options_set_dsn(options, dsn_str);
    sentry_free(dsn_str);

    release_str = call_get_string(env, sentry_sdk_options, release_mid);
    if (release_str) {
        sentry_options_set_release(options, release_str);
        sentry_free(release_str);
    }

    environment_str = call_get_string(env, sentry_sdk_options, environment_mid);
    if (environment_str)
    {
        sentry_options_set_environment(options, environment_str);
        sentry_free(environment_str);
    }

    dist_str = call_get_string(env, sentry_sdk_options, dist_mid);
    if (dist_str)
    {
        sentry_options_set_dist(options, dist_str);
        sentry_free(dist_str);
    }

    sentry_init(options);
    return;

fail:
    sentry_free(outbox_path);
    sentry_transport_free(transport);
    sentry_options_free(options);
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_NativeModuleListLoader_nativeClearModuleList(JNIEnv *env, jclass cls) {
    sentry_clear_modulecache();
}

JNIEXPORT jobjectArray JNICALL
Java_io_sentry_android_ndk_NativeModuleListLoader_nativeLoadModuleList(JNIEnv *env, jclass cls) {
    sentry_value_t image_list_t = sentry_get_modules_list();
    jobjectArray image_list = NULL;

    if (sentry_value_get_type(image_list_t) == SENTRY_VALUE_TYPE_LIST) {
        size_t len_t = sentry_value_get_length(image_list_t);

        jclass image_class = (*env)->FindClass(env, "io/sentry/protocol/DebugImage");
        image_list = (*env)->NewObjectArray(env, len_t, image_class, NULL);

        jmethodID image_addr_method = (*env)->GetMethodID(env, image_class, "setImageAddr",
                                                          "(Ljava/lang/String;)V");

        jmethodID image_size_method = (*env)->GetMethodID(env, image_class, "setImageSize",
                                                          "(J)V");

        jmethodID code_file_method = (*env)->GetMethodID(env, image_class, "setCodeFile",
                                                         "(Ljava/lang/String;)V");

        jmethodID image_addr_ctor = (*env)->GetMethodID(env, image_class, "<init>",
                                                        "()V");

        jmethodID type_method = (*env)->GetMethodID(env, image_class, "setType",
                                                    "(Ljava/lang/String;)V");

        jmethodID debug_id_method = (*env)->GetMethodID(env, image_class, "setDebugId",
                                                        "(Ljava/lang/String;)V");

        jmethodID code_id_method = (*env)->GetMethodID(env, image_class, "setCodeId",
                                                       "(Ljava/lang/String;)V");

        jmethodID debug_file_method = (*env)->GetMethodID(env, image_class, "setDebugFile",
                                                          "(Ljava/lang/String;)V");

        for (size_t i = 0; i < len_t; i++) {
            sentry_value_t image_t = sentry_value_get_by_index(image_list_t, i);

            if (!sentry_value_is_null(image_t)) {
                jobject image = (*env)->NewObject(env, image_class, image_addr_ctor);

                sentry_value_t image_addr_t = sentry_value_get_by_key(image_t, "image_addr");
                if (!sentry_value_is_null(image_addr_t)) {

                    const char *value_v = sentry_value_as_string(image_addr_t);
                    jstring value = (*env)->NewStringUTF(env, value_v);

                    (*env)->CallVoidMethod(env, image, image_addr_method, value);

                    // Local refs (eg NewStringUTF) are freed automatically when the native method
                    // returns, but if you're iterating a large array, it's recommended to release
                    // manually due to allocation limits (512) on Android < 8 or OOM.
                    // https://developer.android.com/training/articles/perf-jni.html#local-and-global-references
                    (*env)->DeleteLocalRef(env, value);
                }

                sentry_value_t image_size_t = sentry_value_get_by_key(image_t, "image_size");
                if (!sentry_value_is_null(image_size_t)) {

                    int32_t value_v = sentry_value_as_int32(image_size_t);
                    jlong value = (jlong) value_v;

                    (*env)->CallVoidMethod(env, image, image_size_method, value);
                }

                sentry_value_t code_file_t = sentry_value_get_by_key(image_t, "code_file");
                if (!sentry_value_is_null(code_file_t)) {

                    const char *value_v = sentry_value_as_string(code_file_t);
                    jstring value = (*env)->NewStringUTF(env, value_v);

                    (*env)->CallVoidMethod(env, image, code_file_method, value);

                    (*env)->DeleteLocalRef(env, value);
                }

                sentry_value_t code_type_t = sentry_value_get_by_key(image_t, "type");
                if (!sentry_value_is_null(code_type_t)) {

                    const char *value_v = sentry_value_as_string(code_type_t);
                    jstring value = (*env)->NewStringUTF(env, value_v);

                    (*env)->CallVoidMethod(env, image, type_method, value);

                    (*env)->DeleteLocalRef(env, value);
                }

                sentry_value_t debug_id_t = sentry_value_get_by_key(image_t, "debug_id");
                if (!sentry_value_is_null(code_type_t)) {

                    const char *value_v = sentry_value_as_string(debug_id_t);
                    jstring value = (*env)->NewStringUTF(env, value_v);

                    (*env)->CallVoidMethod(env, image, debug_id_method, value);

                    (*env)->DeleteLocalRef(env, value);
                }

                sentry_value_t code_id_t = sentry_value_get_by_key(image_t, "code_id");
                if (!sentry_value_is_null(code_id_t)) {

                    const char *value_v = sentry_value_as_string(code_id_t);
                    jstring value = (*env)->NewStringUTF(env, value_v);

                    (*env)->CallVoidMethod(env, image, code_id_method, value);

                    (*env)->DeleteLocalRef(env, value);
                }

                // not needed on Android, but keeping for forward compatibility
                sentry_value_t debug_file_t = sentry_value_get_by_key(image_t, "debug_file");
                if (!sentry_value_is_null(debug_file_t)) {

                    const char *value_v = sentry_value_as_string(debug_file_t);
                    jstring value = (*env)->NewStringUTF(env, value_v);

                    (*env)->CallVoidMethod(env, image, debug_file_method, value);

                    (*env)->DeleteLocalRef(env, value);
                }

                (*env)->SetObjectArrayElement(env, image_list, i, image);

                (*env)->DeleteLocalRef(env, image);
            }
        }

        sentry_value_decref(image_list_t);
    }

    return image_list;
}

JNIEXPORT void JNICALL
Java_io_sentry_android_ndk_SentryNdk_shutdown(JNIEnv *env, jclass cls) {
    sentry_close();
}
