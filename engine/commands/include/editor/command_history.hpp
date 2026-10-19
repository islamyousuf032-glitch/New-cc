#pragma once

#include "editor/timeline.hpp"

#include <cstddef>
#include <memory>
#include <string>
#include <vector>

namespace editor {

class EditCommand {
public:
    virtual ~EditCommand() = default;
    virtual void apply(Timeline& timeline) = 0;
    virtual void undo(Timeline& timeline) = 0;
};

class MoveClipCommand final : public EditCommand {
public:
    MoveClipCommand(std::string clip_id, std::string destination_track_id, Timestamp destination_start);
    void apply(Timeline& timeline) override;
    void undo(Timeline& timeline) override;

private:
    std::string clip_id_;
    std::string destination_track_id_;
    Timestamp destination_start_;
    ClipLocation before_{};
    bool captured_{false};
    bool applied_{false};
};

class RelinkClipMediaCommand final : public EditCommand {
public:
    RelinkClipMediaCommand(std::string clip_id, std::string replacement_media_id);
    void apply(Timeline& timeline) override;
    void undo(Timeline& timeline) override;

private:
    std::string clip_id_;
    std::string replacement_media_id_;
    std::string previous_media_id_;
    std::vector<std::string> affected_clip_ids_;
    bool captured_{false};
    bool applied_{false};
};

class SplitClipCommand final : public EditCommand {
public:
    SplitClipCommand(std::string clip_id, Timestamp cut_at, std::string right_clip_id);
    void apply(Timeline& timeline) override;
    void undo(Timeline& timeline) override;

private:
    std::string clip_id_;
    Timestamp cut_at_;
    std::string right_clip_id_;
    ClipLocation before_{};
    bool captured_{false};
    bool applied_{false};
};

enum class TrimEdge { left, right };

class TrimClipCommand final : public EditCommand {
public:
    TrimClipCommand(std::string clip_id, TrimEdge edge, Timestamp source_boundary);
    void apply(Timeline& timeline) override;
    void undo(Timeline& timeline) override;

private:
    std::string clip_id_;
    TrimEdge edge_;
    Timestamp source_boundary_;
    ClipLocation before_{};
    Clip after_{};
    bool captured_{false};
    bool applied_{false};
};

class DeleteClipCommand final : public EditCommand {
public:
    explicit DeleteClipCommand(std::string clip_id);
    void apply(Timeline& timeline) override;
    void undo(Timeline& timeline) override;

private:
    std::string clip_id_;
    ClipLocation before_{};
    bool captured_{false};
    bool applied_{false};
};

class RippleDeleteClipCommand final : public EditCommand {
public:
    explicit RippleDeleteClipCommand(std::string clip_id);
    void apply(Timeline& timeline) override;
    void undo(Timeline& timeline) override;

private:
    std::string clip_id_;
    RippleDeleteResult result_{};
    bool captured_{false};
    bool applied_{false};
};

class CommandHistory final {
public:
    explicit CommandHistory(std::size_t max_depth = 256);
    void execute(Timeline& timeline, std::unique_ptr<EditCommand> command);
    [[nodiscard]] bool undo(Timeline& timeline);
    [[nodiscard]] bool redo(Timeline& timeline);
    [[nodiscard]] bool can_undo() const noexcept { return !undo_.empty(); }
    [[nodiscard]] bool can_redo() const noexcept { return !redo_.empty(); }
    void clear() noexcept;

private:
    std::size_t max_depth_;
    std::vector<std::unique_ptr<EditCommand>> undo_;
    std::vector<std::unique_ptr<EditCommand>> redo_;
};

} // namespace editor
