#include <jni.h>

#include "editor/session_coordinator.hpp"
#include "editor/project.hpp"
#include "editor/time.hpp"
#include "editor/timeline.hpp"

#include <atomic>
#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>
#include <utility>

namespace {
using namespace editor;

const TimeBase kFrameTimeBase{1, 30};
const TimeBase kMilliseconds{1, 1000};
std::atomic<std::uint64_t> g_project_sequence{1};

using EngineSession = SessionCoordinator;

EngineSession* from_handle(jlong handle) noexcept {
    return reinterpret_cast<EngineSession*>(static_cast<std::uintptr_t>(handle));
}

std::string from_java(JNIEnv* env, jstring value) {
    if (value == nullptr) throw std::invalid_argument("string argument is null");
    const char* utf = env->GetStringUTFChars(value, nullptr);
    if (utf == nullptr) throw std::runtime_error("could not access Java string");
    struct UtfCharsGuard final {
        JNIEnv* env;
        jstring value;
        const char* chars;
        ~UtfCharsGuard() { env->ReleaseStringUTFChars(value, chars); }
    } guard{env, value, utf};
    std::string result(utf);
    if (result.empty()) throw std::invalid_argument("string argument must not be empty");
    return result;
}

void throw_java_argument(JNIEnv* env, const char* message) noexcept {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass("java/lang/IllegalArgumentException");
    if (type != nullptr) env->ThrowNew(type, message);
}

EngineSession& require_session(jlong handle) {
    auto* session = from_handle(handle);
    if (session == nullptr) throw std::invalid_argument("native engine session is closed");
    return *session;
}

void append_json_string(std::string& output, const std::string& value) {
    constexpr char hex[] = "0123456789abcdef";
    output.push_back('"');
    for (const char raw_byte : value) {
        const auto byte = static_cast<unsigned char>(raw_byte);
        switch (byte) {
            case '"': output += "\\\""; break;
            case '\\': output += "\\\\"; break;
            case '\b': output += "\\b"; break;
            case '\f': output += "\\f"; break;
            case '\n': output += "\\n"; break;
            case '\r': output += "\\r"; break;
            case '\t': output += "\\t"; break;
            default:
                if (byte < 0x20) {
                    output += "\\u00";
                    output.push_back(hex[(byte >> 4) & 0x0f]);
                    output.push_back(hex[byte & 0x0f]);
                } else {
                    output.push_back(static_cast<char>(byte));
                }
        }
    }
    output.push_back('"');
}

std::string make_timeline_snapshot(const EngineSession& session) {
    std::string output = "{\"clips\":[";
    bool first = true;
    std::size_t offset = 0;
    std::optional<std::uint64_t> revision;
    for (;;) {
        const auto page = session.clip_page(offset, SessionCoordinator::max_page_size, revision);
        revision = page.revision;
        for (const auto& location : page.clips) {
            const auto& clip = location.clip;
            if (!first) output.push_back(',');
            first = false;
            const auto start = clip.timeline_start.rescaled(kFrameTimeBase).ticks;
            const auto duration = (clip.source_out - clip.source_in).rescaled(kFrameTimeBase).ticks;
            const auto source_in_ms = clip.source_in.rescaled(kMilliseconds).ticks;
            const auto source_out_ms = clip.source_out.rescaled(kMilliseconds).ticks;
            output += "{\"id\":";
            append_json_string(output, clip.id);
            output += ",\"trackId\":";
            append_json_string(output, location.track_id);
            output += ",\"mediaId\":";
            append_json_string(output, clip.media_id);
            output += ",\"startFrame\":" + std::to_string(start);
            output += ",\"durationFrames\":" + std::to_string(duration);
            output += ",\"sourceInMs\":" + std::to_string(source_in_ms);
            output += ",\"sourceOutMs\":" + std::to_string(source_out_ms) + "}";
        }
        offset = page.next_offset;
        if (offset == page.total) break;
    }
    output += "]}";
    return output;
}

std::string make_status(const EngineSession& session) {
    const auto status = session.status();
    return "C++ engine ready · project=" + status.name +
        " · tracks=" + std::to_string(status.tracks) +
        " · clips=" + std::to_string(status.clips) +
        " · undo=" + (status.can_undo ? "yes" : "no") +
        " · redo=" + (status.can_redo ? "yes" : "no");
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_owncut_app_EngineSession_nativeCreate(JNIEnv* env, jobject) {
    try {
        auto project_id = "android-project-" + std::to_string(g_project_sequence.fetch_add(1));
        auto session = std::make_unique<EngineSession>(std::move(project_id));
        return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(session.release()));
    } catch (const std::exception& error) {
        throw_java_argument(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_owncut_app_EngineSession_nativeGetStatus(JNIEnv* env, jobject, jlong handle) {
    try {
        auto& session = require_session(handle);
        const auto status = make_status(session);
        return env->NewStringUTF(status.c_str());
    } catch (const std::exception& error) {
        throw_java_argument(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_owncut_app_EngineSession_nativeGetTimelineSnapshot(JNIEnv* env, jobject, jlong handle) {
    try {
        auto& session = require_session(handle);
        const auto snapshot = make_timeline_snapshot(session);
        return env->NewStringUTF(snapshot.c_str());
    } catch (const std::exception& error) {
        throw_java_argument(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_owncut_app_EngineSession_nativeResolvePlayhead(JNIEnv* env, jobject, jlong handle,
                                                        jstring track_id, jlong frame) {
    try {
        auto& session = require_session(handle);
        const auto track = from_java(env, track_id);
        const auto resolved = session.resolve_playhead(track, Timestamp{frame, kFrameTimeBase});
        if (!resolved) return nullptr;
        std::string json{"{\"clipId\":"};
        append_json_string(json, resolved->clip_id);
        json += ",\"timelineFrame\":" + std::to_string(resolved->timeline_position.ticks);
        json += ",\"sourcePositionMs\":" + std::to_string(resolved->source_position.rescaled(kMilliseconds).ticks) + "}";
        return env->NewStringUTF(json.c_str());
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); return nullptr; }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_owncut_app_EngineSession_nativeAddMediaClip(JNIEnv* env, jobject, jlong handle,
                                                      jstring media_id, jlong duration_ms) {
    try {
        auto& session = require_session(handle);
        auto media = from_java(env, media_id);
        const auto id = session.add_media(media, duration_ms);
        return env->NewStringUTF(id.c_str());
    } catch (const std::exception& error) {
        throw_java_argument(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeRelinkClipMedia(JNIEnv* env, jobject, jlong handle,
                                                          jstring clip_id, jstring replacement_media_id) {
    try {
        auto& session = require_session(handle);
        auto clip = from_java(env, clip_id);
        auto media = from_java(env, replacement_media_id);
        session.execute(
            std::make_unique<RelinkClipMediaCommand>(clip, media));
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeMoveClip(JNIEnv* env, jobject, jlong handle,
                                                  jstring clip_id, jstring destination_track_id,
                                                  jlong destination_start_frame) {
    try {
        auto& session = require_session(handle);
        auto clip = from_java(env, clip_id);
        auto destination = from_java(env, destination_track_id);
        session.execute(
            std::make_unique<MoveClipCommand>(clip, destination,
                                               Timestamp{destination_start_frame, kFrameTimeBase}));
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeSplitClip(JNIEnv* env, jobject, jlong handle,
                                                   jstring clip_id, jlong cut_frame, jstring right_clip_id) {
    try {
        auto& session = require_session(handle);
        auto clip = from_java(env, clip_id);
        auto right = from_java(env, right_clip_id);
        session.execute(
            std::make_unique<SplitClipCommand>(clip, Timestamp{cut_frame, kFrameTimeBase}, right));
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeTrimClip(JNIEnv* env, jobject, jlong handle,
                                                  jstring clip_id, jboolean left_edge, jlong boundary_ms) {
    try {
        auto& session = require_session(handle);
        auto clip = from_java(env, clip_id);
        session.execute(
            std::make_unique<TrimClipCommand>(clip, left_edge == JNI_TRUE ? TrimEdge::left : TrimEdge::right,
                                               Timestamp{boundary_ms, kMilliseconds}));
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeDeleteClip(JNIEnv* env, jobject, jlong handle,
                                                    jstring clip_id, jboolean ripple) {
    try {
        auto& session = require_session(handle);
        auto clip = from_java(env, clip_id);
        if (ripple == JNI_TRUE) {
            session.execute(std::make_unique<RippleDeleteClipCommand>(clip));
        } else {
            session.execute(std::make_unique<DeleteClipCommand>(clip));
        }
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_owncut_app_EngineSession_nativeUndo(JNIEnv* env, jobject, jlong handle) {
    try {
        auto& session = require_session(handle);
        return session.undo() ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& error) {
        throw_java_argument(env, error.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_owncut_app_EngineSession_nativeRedo(JNIEnv* env, jobject, jlong handle) {
    try {
        auto& session = require_session(handle);
        return session.redo() ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& error) {
        throw_java_argument(env, error.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeSaveProject(JNIEnv* env, jobject, jlong handle, jstring path) {
    try {
        auto& session = require_session(handle);
        auto file_path = from_java(env, path);
        session.save(file_path);
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeLoadProject(JNIEnv* env, jobject, jlong handle, jstring path) {
    try {
        auto& session = require_session(handle);
        auto file_path = from_java(env, path);
        session.load(file_path);
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    delete from_handle(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeSaveAutosave(JNIEnv* env, jobject, jlong handle, jstring path) {
    try {
        auto& session = require_session(handle);
        auto file_path = from_java(env, path);
        session.autosave(file_path);
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_owncut_app_EngineSession_nativeLoadAutosave(JNIEnv* env, jobject, jlong handle, jstring path) {
    try {
        auto& session = require_session(handle);
        auto file_path = from_java(env, path);
        return session.recover(file_path) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& error) {
        throw_java_argument(env, error.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeClearAutosave(JNIEnv* env, jobject, jlong handle, jstring path) {
    try {
        require_session(handle).clear_recovery(from_java(env, path));
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeSaveCopy(JNIEnv* env, jobject, jlong handle, jstring path) {
    try { require_session(handle).save_copy(from_java(env, path)); }
    catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}
extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeRestoreDraft(JNIEnv* env, jobject, jlong handle, jstring path) {
    try { require_session(handle).restore_draft(from_java(env, path)); }
    catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}
