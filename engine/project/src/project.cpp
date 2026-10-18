#include "editor/project.hpp"

#include <algorithm>
#include <atomic>
#include <bit>
#include <chrono>
#include <cstdint>
#include <fstream>
#include <iterator>
#include <ostream>
#include <stdexcept>
#include <string_view>
#include <system_error>
#include <utility>

namespace editor {
namespace {
constexpr char kMagic[] = {'O', 'W', 'N', 'C', 'U', 'T', 'P', 'J'};
constexpr std::uint32_t kMaxStringBytes = 4096;
constexpr std::uint32_t kMaxTracks = 4096;
constexpr std::uint32_t kMaxClips = 8192;

void write_u8(std::ostream& out, std::uint8_t value) {
    out.put(static_cast<char>(value));
    if (!out) throw std::runtime_error("failed writing project data");
}
void write_u32(std::ostream& out, std::uint32_t value) {
    for (unsigned shift = 0; shift < 32; shift += 8) write_u8(out, static_cast<std::uint8_t>(value >> shift));
}
void write_u64(std::ostream& out, std::uint64_t value) {
    for (unsigned shift = 0; shift < 64; shift += 8) write_u8(out, static_cast<std::uint8_t>(value >> shift));
}
void write_i64(std::ostream& out, std::int64_t value) { write_u64(out, std::bit_cast<std::uint64_t>(value)); }
bool is_valid_utf8(std::string_view text) noexcept {
    std::size_t index = 0;
    while (index < text.size()) {
        const auto first = static_cast<unsigned char>(text[index]);
        if (first == 0) return false;
        if (first <= 0x7f) { ++index; continue; }
        std::uint32_t codepoint = 0;
        std::size_t continuation_count = 0;
        if (first >= 0xc2 && first <= 0xdf) { codepoint = first & 0x1f; continuation_count = 1; }
        else if (first >= 0xe0 && first <= 0xef) { codepoint = first & 0x0f; continuation_count = 2; }
        else if (first >= 0xf0 && first <= 0xf4) { codepoint = first & 0x07; continuation_count = 3; }
        else return false;
        if (index + continuation_count >= text.size()) return false;
        for (std::size_t offset = 1; offset <= continuation_count; ++offset) {
            const auto byte = static_cast<unsigned char>(text[index + offset]);
            if ((byte & 0xc0) != 0x80) return false;
            codepoint = (codepoint << 6) | (byte & 0x3f);
        }
        if ((continuation_count == 2 && codepoint < 0x800) ||
            (continuation_count == 3 && codepoint < 0x10000) ||
            codepoint > 0x10ffff || (codepoint >= 0xd800 && codepoint <= 0xdfff)) return false;
        index += continuation_count + 1;
    }
    return true;
}

void write_string(std::ostream& out, const std::string& value) {
    if (value.size() > kMaxStringBytes) throw std::invalid_argument("project string exceeds size limit");
    if (!is_valid_utf8(value)) throw std::invalid_argument("project strings must be valid UTF-8 without NUL bytes");
    write_u32(out, static_cast<std::uint32_t>(value.size()));
    out.write(value.data(), static_cast<std::streamsize>(value.size()));
    if (!out) throw std::runtime_error("failed writing project string");
}

std::uint8_t read_u8(std::istream& in) {
    const int value = in.get();
    if (value == std::char_traits<char>::eof()) throw std::runtime_error("truncated project data");
    return static_cast<std::uint8_t>(value);
}
std::uint32_t read_u32(std::istream& in) {
    std::uint32_t value = 0;
    for (unsigned shift = 0; shift < 32; shift += 8)
        value |= static_cast<std::uint32_t>(read_u8(in)) << shift;
    return value;
}
std::uint64_t read_u64(std::istream& in) {
    std::uint64_t value = 0;
    for (unsigned shift = 0; shift < 64; shift += 8)
        value |= static_cast<std::uint64_t>(read_u8(in)) << shift;
    return value;
}
std::int64_t read_i64(std::istream& in) { return std::bit_cast<std::int64_t>(read_u64(in)); }
std::string read_string(std::istream& in) {
    const auto length = read_u32(in);
    if (length > kMaxStringBytes) throw std::runtime_error("project string exceeds size limit");
    std::string value(length, '\0');
    in.read(value.data(), static_cast<std::streamsize>(length));
    if (in.gcount() != static_cast<std::streamsize>(length)) throw std::runtime_error("truncated project string");
    if (!is_valid_utf8(value)) throw std::runtime_error("project string is not valid UTF-8");
    return value;
}
void write_timebase(std::ostream& out, TimeBase value) {
    write_i64(out, value.numerator);
    write_i64(out, value.denominator);
}
TimeBase read_timebase(std::istream& in) { return {read_i64(in), read_i64(in)}; }
void write_timestamp(std::ostream& out, Timestamp value) {
    write_i64(out, value.ticks);
    write_timebase(out, value.time_base);
}
Timestamp read_timestamp(std::istream& in) {
    const auto ticks = read_i64(in);
    return {ticks, read_timebase(in)};
}
void require_bool(std::uint8_t value) {
    if (value > 1) throw std::runtime_error("invalid boolean field in project");
}
void require_no_trailing_data(std::istream& in) {
    if (in.peek() != std::char_traits<char>::eof()) throw std::runtime_error("unexpected trailing project data");
    if (in.bad()) throw std::runtime_error("failed reading project data");
}

std::filesystem::path make_temp_path(const std::filesystem::path& target) {
    static std::atomic<std::uint64_t> sequence{0};
    const auto now = std::chrono::steady_clock::now().time_since_epoch().count();
    auto temp = target;
    temp += ".tmp." + std::to_string(now) + "." + std::to_string(sequence.fetch_add(1));
    return temp;
}
} // namespace

Project::Project(std::string project_id, std::string project_name, TimeBase frame_time_base)
    : id(std::move(project_id)), name(std::move(project_name)), timeline(frame_time_base) {}

void ProjectCodec::write(const Project& project, std::ostream& output) {
    if (project.id.empty() || project.name.empty()) throw std::invalid_argument("project id and name are required");
    const auto& tracks = project.timeline.tracks();
    if (tracks.size() > kMaxTracks) throw std::invalid_argument("project track limit exceeded");

    output.write(kMagic, sizeof(kMagic));
    if (!output) throw std::runtime_error("failed writing project header");
    write_u32(output, current_version);
    write_string(output, project.id);
    write_string(output, project.name);
    write_timebase(output, project.timeline.frame_time_base());
    write_u32(output, static_cast<std::uint32_t>(tracks.size()));

    std::uint64_t total_clips = 0;
    for (const auto& track : tracks) {
        total_clips += track.clips.size();
        if (total_clips > kMaxClips) throw std::invalid_argument("project clip limit exceeded");
        write_string(output, track.id);
        write_u8(output, static_cast<std::uint8_t>(track.kind));
        write_u8(output, track.muted ? 1 : 0);
        write_u8(output, track.locked ? 1 : 0);
        write_u32(output, static_cast<std::uint32_t>(track.clips.size()));
        for (const auto& clip : track.clips) {
            write_string(output, clip.id);
            write_string(output, clip.media_id);
            write_timestamp(output, clip.timeline_start);
            write_timestamp(output, clip.source_in);
            write_timestamp(output, clip.source_out);
        }
    }
    if (!output) throw std::runtime_error("failed writing project data");
}

Project ProjectCodec::read(std::istream& input) {
    char magic[sizeof(kMagic)]{};
    input.read(magic, sizeof(magic));
    if (input.gcount() != static_cast<std::streamsize>(sizeof(magic)) ||
        !std::equal(std::begin(magic), std::end(magic), std::begin(kMagic)))
        throw std::runtime_error("invalid project header");

    const auto version = read_u32(input);
    if (version > current_version) throw std::runtime_error("project schema version is newer than this editor");
    if (version != 0 && version != current_version) throw std::runtime_error("unsupported project schema version");

    auto id = read_string(input);
    auto name = read_string(input);
    if (id.empty() || name.empty()) throw std::runtime_error("project id and name are required");
    const auto frame_time_base = read_timebase(input);
    const auto track_count = read_u32(input);
    if (track_count > kMaxTracks) throw std::runtime_error("project track limit exceeded");
    Project project{std::move(id), std::move(name), frame_time_base};

    std::uint64_t total_clips = 0;
    for (std::uint32_t track_index = 0; track_index < track_count; ++track_index) {
        Track track{read_string(input), TrackKind::video, false, false, {}};
        const auto kind = read_u8(input);
        if (kind > static_cast<std::uint8_t>(TrackKind::audio)) throw std::runtime_error("invalid track kind");
        track.kind = static_cast<TrackKind>(kind);
        std::uint8_t muted = 0;
        std::uint8_t locked = 0;
        if (version >= 1) {
            muted = read_u8(input);
            locked = read_u8(input);
            require_bool(muted);
            require_bool(locked);
        }
        const auto clip_count = read_u32(input);
        total_clips += clip_count;
        if (total_clips > kMaxClips) throw std::runtime_error("project clip limit exceeded");
        const auto track_id = track.id;
        project.timeline.add_track(std::move(track));

        for (std::uint32_t clip_index = 0; clip_index < clip_count; ++clip_index) {
            Clip clip{read_string(input), read_string(input), read_timestamp(input), read_timestamp(input), read_timestamp(input)};
            project.timeline.add_clip(track_id, std::move(clip));
        }
        project.timeline.set_track_state(track_id, muted != 0, locked != 0);
    }
    require_no_trailing_data(input);
    return project;
}

void ProjectStore::save_atomic(const std::filesystem::path& path, const Project& project) {
    if (path.empty() || path.filename().empty()) throw std::invalid_argument("project path must name a file");
    const auto temp_path = make_temp_path(path);
    try {
        {
            std::ofstream output(temp_path, std::ios::binary | std::ios::out | std::ios::trunc);
            if (!output) throw std::runtime_error("could not create temporary project file");
            ProjectCodec::write(project, output);
            output.flush();
            if (!output) throw std::runtime_error("could not flush temporary project file");
            output.close();
            if (!output) throw std::runtime_error("could not close temporary project file");
        }
        std::error_code error;
        std::filesystem::rename(temp_path, path, error);
        if (error) throw std::filesystem::filesystem_error("could not atomically replace project", temp_path, path, error);
    } catch (...) {
        std::error_code ignored;
        std::filesystem::remove(temp_path, ignored);
        throw;
    }
}

Project ProjectStore::load(const std::filesystem::path& path) {
    std::ifstream input(path, std::ios::binary);
    if (!input) throw std::runtime_error("could not open project file");
    return ProjectCodec::read(input);
}

std::filesystem::path ProjectStore::autosave_path_for(const std::filesystem::path& project_path) {
    if (project_path.empty() || project_path.filename().empty())
        throw std::invalid_argument("project path must name a file");
    auto autosave = project_path;
    autosave += ".autosave";
    return autosave;
}

void ProjectStore::save_autosave(const std::filesystem::path& project_path, const Project& project) {
    save_atomic(autosave_path_for(project_path), project);
}

std::optional<Project> ProjectStore::load_autosave(const std::filesystem::path& project_path) {
    const auto autosave = autosave_path_for(project_path);
    std::error_code error;
    const bool exists = std::filesystem::exists(autosave, error);
    if (error) throw std::filesystem::filesystem_error("could not inspect autosave", autosave, error);
    if (!exists) return std::nullopt;
    return load(autosave);
}

void ProjectStore::clear_autosave(const std::filesystem::path& project_path) {
    const auto autosave = autosave_path_for(project_path);
    std::error_code error;
    (void)std::filesystem::remove(autosave, error);
    if (error) throw std::filesystem::filesystem_error("could not remove autosave", autosave, error);
}
} // namespace editor
