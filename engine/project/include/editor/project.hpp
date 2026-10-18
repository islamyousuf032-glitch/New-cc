#pragma once

#include "editor/timeline.hpp"

#include <filesystem>
#include <iosfwd>
#include <optional>
#include <string>

namespace editor {

struct Project final {
    std::string id;
    std::string name;
    Timeline timeline;

    Project(std::string project_id, std::string project_name, TimeBase frame_time_base);
};

// Versioned, little-endian binary project format. v0 is read-only and migrates
// missing track mute/lock fields to false; writes always emit the current schema.
class ProjectCodec final {
public:
    static constexpr unsigned current_version = 1;
    static void write(const Project& project, std::ostream& output);
    [[nodiscard]] static Project read(std::istream& input);
};

// Same-directory temp write + rename. Platform storage adapters should use this
// codec with their own atomic document-provider strategy on Android SAF URIs.
class ProjectStore final {
public:
    static void save_atomic(const std::filesystem::path& path, const Project& project);
    [[nodiscard]] static Project load(const std::filesystem::path& path);
    static void save_autosave(const std::filesystem::path& project_path, const Project& project);
    [[nodiscard]] static std::optional<Project> load_autosave(const std::filesystem::path& project_path);
    static void clear_autosave(const std::filesystem::path& project_path);
    [[nodiscard]] static std::filesystem::path autosave_path_for(const std::filesystem::path& project_path);
};

} // namespace editor
