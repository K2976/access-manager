#pragma once

#include <cstdint>
#include <cstddef>
#include <memory>

namespace dev {
namespace kartik {
namespace accessmanager {
namespace common {

// Explicit memory ownership definition.
// Memory returned by these structures is explicitly owned by the creator
// and must be cleaned up carefully to prevent native leaks in long-running VPNs.

struct Buffer {
    std::unique_ptr<uint8_t[]> data;
    size_t length;

    Buffer(size_t len) : length(len), data(new uint8_t[len]) {}
};

} // namespace common
} // namespace accessmanager
} // namespace kartik
} // namespace dev
