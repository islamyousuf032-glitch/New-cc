#pragma once
#include "editor/color.hpp"
#include <string>

namespace editor::color {
// Explicit normalized straight-alpha RGB709 texture encodings. NOT raw video/YUV,
// PQ/HLG, Rec2020, premultiplied input, or an auto-detected media color description.
enum class TextureEncoding { srgb = 0, linear709 = 1, media3_sdr709 = 2 };
void validate(TextureEncoding encoding);
[[nodiscard]] Rgb texture_reference(Rgb normalized, const Grade& grade, TextureEncoding encoding);
// Original GLES 2 shader, immutable parameters baked by C++ (no Kotlin grade math).
// Require fragment highp; reject grades whose intermediates cannot safely fit floats.
// Caller owns GL/context/lifetime. Output alpha unchanged, no timing/geometry transform.
[[nodiscard]] std::string grade_vertex_shader();
[[nodiscard]] std::string grade_fragment_shader(const Grade& grade, TextureEncoding encoding);
} // namespace editor::color
