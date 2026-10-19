#pragma once

#include "editor/time.hpp"

#include <cstddef>
#include <string>
#include <utility>
#include <vector>

namespace editor {

enum class TrackKind { video = 0, audio = 1 };

struct Clip final {
    std::string id;
    std::string media_id;
    Timestamp timeline_start;
    Timestamp source_in;
    Timestamp source_out; // exclusive
};

struct Track final {
    std::string id;
    TrackKind kind;
    bool muted{false};
    bool locked{false};
    std::vector<Clip> clips;
};

struct ClipLocation final {
    std::string track_id;
    Clip clip;
};

struct RippleDeleteResult final {
    ClipLocation removed;
    std::size_t original_index{0};
    std::vector<ClipLocation> shifted_before;
};

// A non-destructive value model. Clips refer to source ranges; source media is never modified.
class Timeline final {
public:
    explicit Timeline(TimeBase frame_time_base);
    void add_track(Track track);
    void add_clip(const std::string& track_id, Clip clip);
    [[nodiscard]] ClipLocation remove_clip(const std::string& clip_id);
    void replace_clip(const Clip& replacement);
    void relink_clip_media(const std::string& clip_id, const std::string& media_id);
    [[nodiscard]] Clip split_clip(const std::string& clip_id, Timestamp cut_at,
                                  const std::string& right_clip_id);
    [[nodiscard]] RippleDeleteResult ripple_delete(const std::string& clip_id);
    void restore_ripple_delete(const RippleDeleteResult& result);
    void set_track_state(const std::string& track_id, bool muted, bool locked);
    void move_clip(const std::string& clip_id, const std::string& destination_track_id,
                   Timestamp destination_start);
    [[nodiscard]] ClipLocation locate_clip(const std::string& clip_id) const;
    [[nodiscard]] const std::vector<Track>& tracks() const noexcept { return tracks_; }
    [[nodiscard]] TimeBase frame_time_base() const noexcept { return frame_time_base_; }

private:
    TimeBase frame_time_base_;
    std::vector<Track> tracks_;
};

} // namespace editor
