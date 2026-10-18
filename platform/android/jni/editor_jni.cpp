#include <jni.h>

#include "editor/command_history.hpp"
#include "editor/project.hpp"
#include "editor/time.hpp"
#include "editor/timeline.hpp"

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <utility>

namespace {
using namespace editor;

const TimeBase kFrameTimeBase{1, 30};
const TimeBase kMilliseconds{1, 1000};
std::atomic<std::uint64_t> g_project_sequence{1};

struct EngineSession final {
    explicit EngineSession(std::string project_id)
        : project(std::move(project_id), "Untitled", kFrameTimeBase) {
        project.timeline.add_track({"v1", TrackKind::video, false, false, {}});
        project.timeline.add_track({"a1", TrackKind::audio, false, false, {}});
    }

    Project project;
    CommandHistory history;
    std::uint64_t next_clip_id{1};
    std::mutex mutex;
};

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

std::string next_clip_id(EngineSession& session) {
    for (;;) {
        const std::string id = "clip-" + std::to_string(session.next_clip_id++);
        try { (void)session.project.timeline.locate_clip(id); }
        catch (const std::invalid_argument&) { return id; }
    }
}

std::string add_media_clip(EngineSession& session, const std::string& media_id, std::int64_t duration_ms) {
    if (duration_ms <= 0) throw std::invalid_argument("media duration must be positive");
    auto& timeline = session.project.timeline;
    const auto video_track = std::find_if(timeline.tracks().begin(), timeline.tracks().end(),
        [](const Track& track) { return track.kind == TrackKind::video; });
    if (video_track == timeline.tracks().end()) throw std::logic_error("project has no video track");

    Timestamp append_at{0, kFrameTimeBase};
    for (const auto& clip : video_track->clips) {
        const auto end = clip.timeline_start + (clip.source_out - clip.source_in).rescaled(kFrameTimeBase);
        if (append_at < end) append_at = end;
    }
    const std::string id = next_clip_id(session);
    timeline.add_clip(video_track->id,
        {id, media_id, append_at, Timestamp{0, kMilliseconds}, Timestamp{duration_ms, kMilliseconds}});
    return id;
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
    for (const auto& track : session.project.timeline.tracks()) {
        for (const auto& clip : track.clips) {
            if (!first) output.push_back(',');
            first = false;
            const auto start = clip.timeline_start.rescaled(kFrameTimeBase).ticks;
            const auto duration = (clip.source_out - clip.source_in).rescaled(kFrameTimeBase).ticks;
            const auto source_in_ms = clip.source_in.rescaled(kMilliseconds).ticks;
            const auto source_out_ms = clip.source_out.rescaled(kMilliseconds).ticks;
            output += "{\"id\":";
            append_json_string(output, clip.id);
            output += ",\"trackId\":";
            append_json_string(output, track.id);
            output += ",\"mediaId\":";
            append_json_string(output, clip.media_id);
            output += ",\"startFrame\":" + std::to_string(start);
            output += ",\"durationFrames\":" + std::to_string(duration);
            output += ",\"sourceInMs\":" + std::to_string(source_in_ms);
            output += ",\"sourceOutMs\":" + std::to_string(source_out_ms) + "}";
        }
    }
    output += "]}";
    return output;
}

std::string make_status(const EngineSession& session) {
    std::size_t clip_count = 0;
    for (const auto& track : session.project.timeline.tracks()) clip_count += track.clips.size();
    return "C++ engine ready · project=" + session.project.name +
        " · tracks=" + std::to_string(session.project.timeline.tracks().size()) +
        " · clips=" + std::to_string(clip_count) +
        " · undo=" + (session.history.can_undo() ? "yes" : "no") +
        " · redo=" + (session.history.can_redo() ? "yes" : "no");
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
        std::lock_guard lock(session.mutex);
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
        std::lock_guard lock(session.mutex);
        const auto snapshot = make_timeline_snapshot(session);
        return env->NewStringUTF(snapshot.c_str());
    } catch (const std::exception& error) {
        throw_java_argument(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_owncut_app_EngineSession_nativeAddMediaClip(JNIEnv* env, jobject, jlong handle,
                                                      jstring media_id, jlong duration_ms) {
    try {
        auto& session = require_session(handle);
        auto media = from_java(env, media_id);
        std::lock_guard lock(session.mutex);
        const auto id = add_media_clip(session, media, duration_ms);
        return env->NewStringUTF(id.c_str());
    } catch (const std::exception& error) {
        throw_java_argument(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeSplitClip(JNIEnv* env, jobject, jlong handle,
                                                   jstring clip_id, jlong cut_frame, jstring right_clip_id) {
    try {
        auto& session = require_session(handle);
        auto clip = from_java(env, clip_id);
        auto right = from_java(env, right_clip_id);
        std::lock_guard lock(session.mutex);
        session.history.execute(session.project.timeline,
            std::make_unique<SplitClipCommand>(clip, Timestamp{cut_frame, kFrameTimeBase}, right));
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeTrimClip(JNIEnv* env, jobject, jlong handle,
                                                  jstring clip_id, jboolean left_edge, jlong boundary_ms) {
    try {
        auto& session = require_session(handle);
        auto clip = from_java(env, clip_id);
        std::lock_guard lock(session.mutex);
        session.history.execute(session.project.timeline,
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
        std::lock_guard lock(session.mutex);
        if (ripple == JNI_TRUE) {
            session.history.execute(session.project.timeline, std::make_unique<RippleDeleteClipCommand>(clip));
        } else {
            session.history.execute(session.project.timeline, std::make_unique<DeleteClipCommand>(clip));
        }
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_owncut_app_EngineSession_nativeUndo(JNIEnv* env, jobject, jlong handle) {
    try {
        auto& session = require_session(handle);
        std::lock_guard lock(session.mutex);
        return session.history.undo(session.project.timeline) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& error) {
        throw_java_argument(env, error.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_owncut_app_EngineSession_nativeRedo(JNIEnv* env, jobject, jlong handle) {
    try {
        auto& session = require_session(handle);
        std::lock_guard lock(session.mutex);
        return session.history.redo(session.project.timeline) ? JNI_TRUE : JNI_FALSE;
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
        std::lock_guard lock(session.mutex);
        ProjectStore::save_atomic(file_path, session.project);
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeLoadProject(JNIEnv* env, jobject, jlong handle, jstring path) {
    try {
        auto& session = require_session(handle);
        auto file_path = from_java(env, path);
        std::lock_guard lock(session.mutex);
        auto loaded = ProjectStore::load(file_path); // Parse/validate before replacing live session state.
        session.project = std::move(loaded);
        session.history.clear();
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
        std::lock_guard lock(session.mutex);
        ProjectStore::save_autosave(file_path, session.project);
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_owncut_app_EngineSession_nativeLoadAutosave(JNIEnv* env, jobject, jlong handle, jstring path) {
    try {
        auto& session = require_session(handle);
        auto file_path = from_java(env, path);
        std::lock_guard lock(session.mutex);
        auto recovered = ProjectStore::load_autosave(file_path);
        if (!recovered) return JNI_FALSE;
        session.project = std::move(*recovered);
        session.history.clear();
        return JNI_TRUE;
    } catch (const std::exception& error) {
        throw_java_argument(env, error.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_owncut_app_EngineSession_nativeClearAutosave(JNIEnv* env, jobject, jlong handle, jstring path) {
    try {
        (void)require_session(handle);
        ProjectStore::clear_autosave(from_java(env, path));
    } catch (const std::exception& error) { throw_java_argument(env, error.what()); }
}
