#pragma once

#include <string>
#include <functional>

namespace AndroidDiscIO {

using ProgressCallback = std::function<void(const std::string& status, float progress)>;

struct ExtractionResult {
    bool success = false;
    std::string errorMessage;
    std::string gameId;
    uint16_t revision = 0;
};

// Extracts an RMCP01 PAL .wbfs or .iso file to destDirectory
ExtractionResult ExtractDisc(const std::string& discPath, 
                            const std::string& destDirectory,
                            ProgressCallback progress = nullptr);

// Inspects a container without full extraction
ExtractionResult InspectDisc(const std::string& discPath);

} // namespace AndroidDiscIO
