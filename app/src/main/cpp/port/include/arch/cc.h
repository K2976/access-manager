#ifndef ACCESSMANAGER_CC_H
#define ACCESSMANAGER_CC_H

#include <stdint.h>
#include <stddef.h>
#include <sys/time.h>
#include <inttypes.h>
#include <errno.h>

// Android NDK Compiler configurations for lwIP
#define BYTE_ORDER LITTLE_ENDIAN

// Typedefs for lwIP
typedef uint8_t u8_t;
typedef int8_t s8_t;
typedef uint16_t u16_t;
typedef int16_t s16_t;
typedef uint32_t u32_t;
typedef int32_t s32_t;

typedef uintptr_t mem_ptr_t;

// Printing formats
#define U16_F "hu"
#define S16_F "hd"
#define X16_F "hx"
#define U32_F "u"
#define S32_F "d"
#define X32_F "x"

// Diagnostic macros
#include "../../common/Logger.h"
#define LWIP_PLATFORM_DIAG(x) do { LOGD x; } while(0)
#define LWIP_PLATFORM_ASSERT(x) do { LOGE("Assertion \"%s\" failed at line %d in %s\n", x, __LINE__, __FILE__); } while(0)

// Packed struct macros
#define PACK_STRUCT_FIELD(x) x
#define PACK_STRUCT_STRUCT __attribute__((packed))
#define PACK_STRUCT_BEGIN
#define PACK_STRUCT_END

#endif // ACCESSMANAGER_CC_H
