#pragma once

namespace dev {
namespace kartik {
namespace accessmanager {
namespace common {

// Standardized error codes for JNI callback
enum class ErrorCode {
    SUCCESS = 0,
    INIT_FAILED = 1,
    START_FAILED = 2,
    MEMORY_ALLOCATION_FAILED = 3,
    JNI_EXCEPTION = 4,
    NETWORK_ERROR = 5
};

} // namespace common
} // namespace accessmanager
} // namespace kartik
} // namespace dev
