#include "editor/command_history.hpp"

#include <stdexcept>
#include <utility>

namespace editor {
MoveClipCommand::MoveClipCommand(std::string clip_id, std::string destination_track_id,
                                 Timestamp destination_start)
    : clip_id_(std::move(clip_id)), destination_track_id_(std::move(destination_track_id)),
      destination_start_(destination_start) {
    if (clip_id_.empty() || destination_track_id_.empty())
        throw std::invalid_argument("move command requires clip and destination track ids");
}

void MoveClipCommand::apply(Timeline& timeline) {
    if (applied_) throw std::logic_error("move command is already applied");
    if (!captured_) {
        before_ = timeline.locate_clip(clip_id_);
        captured_ = true;
    }
    timeline.move_clip(clip_id_, destination_track_id_, destination_start_);
    applied_ = true;
}

void MoveClipCommand::undo(Timeline& timeline) {
    if (!captured_ || !applied_) throw std::logic_error("move command is not applied");
    timeline.move_clip(clip_id_, before_.track_id, before_.clip.timeline_start);
    applied_ = false;
}

SplitClipCommand::SplitClipCommand(std::string clip_id, Timestamp cut_at, std::string right_clip_id)
    : clip_id_(std::move(clip_id)), cut_at_(cut_at), right_clip_id_(std::move(right_clip_id)) {
    if (clip_id_.empty() || right_clip_id_.empty() || clip_id_ == right_clip_id_)
        throw std::invalid_argument("split command requires distinct non-empty clip ids");
}

void SplitClipCommand::apply(Timeline& timeline) {
    if (applied_) throw std::logic_error("split command is already applied");
    if (!captured_) { before_ = timeline.locate_clip(clip_id_); captured_ = true; }
    (void)timeline.split_clip(clip_id_, cut_at_, right_clip_id_);
    applied_ = true;
}

void SplitClipCommand::undo(Timeline& timeline) {
    if (!captured_ || !applied_) throw std::logic_error("split command is not applied");
    (void)timeline.remove_clip(right_clip_id_);
    timeline.replace_clip(before_.clip);
    applied_ = false;
}

TrimClipCommand::TrimClipCommand(std::string clip_id, TrimEdge edge, Timestamp source_boundary)
    : clip_id_(std::move(clip_id)), edge_(edge), source_boundary_(source_boundary) {
    if (clip_id_.empty()) throw std::invalid_argument("trim command requires a clip id");
}

void TrimClipCommand::apply(Timeline& timeline) {
    if (applied_) throw std::logic_error("trim command is already applied");
    if (!captured_) {
        before_ = timeline.locate_clip(clip_id_);
        after_ = before_.clip;
        captured_ = true;
    }
    if (edge_ == TrimEdge::left) {
        const auto delta = source_boundary_ - before_.clip.source_in;
        after_.timeline_start = before_.clip.timeline_start + delta;
        after_.source_in = source_boundary_;
    } else {
        after_.source_out = source_boundary_;
    }
    timeline.replace_clip(after_);
    applied_ = true;
}

void TrimClipCommand::undo(Timeline& timeline) {
    if (!captured_ || !applied_) throw std::logic_error("trim command is not applied");
    timeline.replace_clip(before_.clip);
    applied_ = false;
}

DeleteClipCommand::DeleteClipCommand(std::string clip_id) : clip_id_(std::move(clip_id)) {
    if (clip_id_.empty()) throw std::invalid_argument("delete command requires a clip id");
}

void DeleteClipCommand::apply(Timeline& timeline) {
    if (applied_) throw std::logic_error("delete command is already applied");
    if (!captured_) { before_ = timeline.locate_clip(clip_id_); captured_ = true; }
    (void)timeline.remove_clip(clip_id_);
    applied_ = true;
}

void DeleteClipCommand::undo(Timeline& timeline) {
    if (!captured_ || !applied_) throw std::logic_error("delete command is not applied");
    timeline.add_clip(before_.track_id, before_.clip);
    applied_ = false;
}

RippleDeleteClipCommand::RippleDeleteClipCommand(std::string clip_id) : clip_id_(std::move(clip_id)) {
    if (clip_id_.empty()) throw std::invalid_argument("ripple delete requires a clip id");
}

void RippleDeleteClipCommand::apply(Timeline& timeline) {
    if (applied_) throw std::logic_error("ripple delete command is already applied");
    auto result = timeline.ripple_delete(clip_id_);
    if (!captured_) { result_ = std::move(result); captured_ = true; }
    applied_ = true;
}

void RippleDeleteClipCommand::undo(Timeline& timeline) {
    if (!captured_ || !applied_) throw std::logic_error("ripple delete command is not applied");
    timeline.restore_ripple_delete(result_);
    applied_ = false;
}

CommandHistory::CommandHistory(std::size_t max_depth) : max_depth_(max_depth) {
    if (max_depth_ == 0) throw std::invalid_argument("command history depth must be positive");
}

void CommandHistory::execute(Timeline& timeline, std::unique_ptr<EditCommand> command) {
    if (!command) throw std::invalid_argument("cannot execute a null command");
    undo_.push_back(std::move(command));
    try {
        undo_.back()->apply(timeline);
    } catch (...) {
        undo_.pop_back();
        throw;
    }
    redo_.clear();
    if (undo_.size() > max_depth_)
        undo_.erase(undo_.begin(), undo_.begin() + static_cast<std::ptrdiff_t>(undo_.size() - max_depth_));
}

bool CommandHistory::undo(Timeline& timeline) {
    if (undo_.empty()) return false;
    redo_.push_back(std::move(undo_.back()));
    undo_.pop_back();
    try {
        redo_.back()->undo(timeline);
    } catch (...) {
        undo_.push_back(std::move(redo_.back()));
        redo_.pop_back();
        throw;
    }
    return true;
}

bool CommandHistory::redo(Timeline& timeline) {
    if (redo_.empty()) return false;
    undo_.push_back(std::move(redo_.back()));
    redo_.pop_back();
    try {
        undo_.back()->apply(timeline);
    } catch (...) {
        redo_.push_back(std::move(undo_.back()));
        undo_.pop_back();
        throw;
    }
    return true;
}

void CommandHistory::clear() noexcept {
    undo_.clear();
    redo_.clear();
}
} // namespace editor
