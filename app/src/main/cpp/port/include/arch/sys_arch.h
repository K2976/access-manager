#ifndef ACCESSMANAGER_SYS_ARCH_H
#define ACCESSMANAGER_SYS_ARCH_H

#include "arch/cc.h"

// Semaphores
typedef void* sys_sem_t;
#define SYS_SEM_NULL nullptr

// Mutexes
typedef void* sys_mutex_t;
#define SYS_MUTEX_NULL nullptr

// Mailboxes
typedef void* sys_mbox_t;
#define SYS_MBOX_NULL nullptr

// Threads
typedef void* sys_thread_t;

// Protection
typedef u32_t sys_prot_t;

#ifdef __cplusplus
extern "C" {
#endif

// Time
u32_t sys_now(void);
void sys_init(void);

sys_prot_t sys_arch_protect(void);
void sys_arch_unprotect(sys_prot_t pval);

#define SYS_ARCH_DECL_PROTECT(lev) sys_prot_t lev
#define SYS_ARCH_PROTECT(lev) lev = sys_arch_protect()
#define SYS_ARCH_UNPROTECT(lev) sys_arch_unprotect(lev)

#ifdef __cplusplus
}
#endif

#endif // ACCESSMANAGER_SYS_ARCH_H
