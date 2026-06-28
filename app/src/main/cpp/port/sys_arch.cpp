#include "arch/sys_arch.h"
#include <chrono>
#include <vector>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <thread>
#include "lwip/err.h"
#include "lwip/sys.h"

std::mutex g_sys_prot_mtx;

static auto start_time = std::chrono::steady_clock::now();

extern "C" {

sys_prot_t sys_arch_protect(void) {
    g_sys_prot_mtx.lock();
    return 1;
}

void sys_arch_unprotect(sys_prot_t pval) {
    g_sys_prot_mtx.unlock();
}

u32_t sys_now(void) {
    auto now = std::chrono::steady_clock::now();
    return std::chrono::duration_cast<std::chrono::milliseconds>(now - start_time).count();
}

void sys_init(void) {
    // Nothing to do globally for std::thread environment
}

} // extern "C"

// -- Mutexes --

struct sys_mutex_internal {
    std::mutex mtx;
    bool is_valid = false;
};

err_t sys_mutex_new(sys_mutex_t *mutex) {
    auto m = new sys_mutex_internal();
    m->is_valid = true;
    *mutex = static_cast<void*>(m);
    return ERR_OK;
}

void sys_mutex_lock(sys_mutex_t *mutex) {
    auto m = static_cast<sys_mutex_internal*>(*mutex);
    if (!m || !m->is_valid) return;
    m->mtx.lock();
}

void sys_mutex_unlock(sys_mutex_t *mutex) {
    auto m = static_cast<sys_mutex_internal*>(*mutex);
    if (!m || !m->is_valid) return;
    m->mtx.unlock();
}

void sys_mutex_free(sys_mutex_t *mutex) {
    auto m = static_cast<sys_mutex_internal*>(*mutex);
    if (!m || !m->is_valid) return;
    m->is_valid = false;
    delete m;
    *mutex = nullptr;
}

// -- Semaphores --

struct sys_sem_internal {
    std::mutex mtx;
    std::condition_variable cv;
    int count = 0;
    bool is_valid = false;
};

err_t sys_sem_new(sys_sem_t *sem, u8_t count) {
    auto s = new sys_sem_internal();
    s->count = count;
    s->is_valid = true;
    *sem = static_cast<void*>(s);
    return ERR_OK;
}

void sys_sem_signal(sys_sem_t *sem) {
    auto s = static_cast<sys_sem_internal*>(*sem);
    if (!s || !s->is_valid) return;
    std::lock_guard<std::mutex> lock(s->mtx);
    s->count++;
    s->cv.notify_one();
}

u32_t sys_arch_sem_wait(sys_sem_t *sem, u32_t timeout) {
    auto s = static_cast<sys_sem_internal*>(*sem);
    if (!s || !s->is_valid) return SYS_ARCH_TIMEOUT;
    
    std::unique_lock<std::mutex> lock(s->mtx);
    
    if (timeout == 0) {
        auto start = sys_now();
        s->cv.wait(lock, [&]{ return s->count > 0 || !s->is_valid; });
        if (!s->is_valid) return SYS_ARCH_TIMEOUT;
        s->count--;
        return sys_now() - start;
    } else {
        auto start = sys_now();
        bool success = s->cv.wait_for(lock, std::chrono::milliseconds(timeout), [&]{ return s->count > 0 || !s->is_valid; });
        if (!success || !s->is_valid) return SYS_ARCH_TIMEOUT;
        s->count--;
        return sys_now() - start;
    }
}

void sys_sem_free(sys_sem_t *sem) {
    auto s = static_cast<sys_sem_internal*>(*sem);
    if (!s || !s->is_valid) return;
    {
        std::lock_guard<std::mutex> lock(s->mtx);
        s->is_valid = false;
        s->cv.notify_all();
    }
    delete s;
    *sem = nullptr;
}

int sys_sem_valid(sys_sem_t *sem) {
    if (!sem) return 0;
    auto s = static_cast<sys_sem_internal*>(*sem);
    return s && s->is_valid;
}

void sys_sem_set_invalid(sys_sem_t *sem) {
    if (sem && *sem) {
        auto s = static_cast<sys_sem_internal*>(*sem);
        s->is_valid = false;
    }
}

// -- Mailboxes --

struct sys_mbox_internal {
    std::mutex mtx;
    std::condition_variable cv;
    std::queue<void*> q;
    int size = 0;
    bool is_valid = false;
};

err_t sys_mbox_new(sys_mbox_t *mbox, int size) {
    auto m = new sys_mbox_internal();
    m->size = size;
    m->is_valid = true;
    *mbox = static_cast<void*>(m);
    return ERR_OK;
}

void sys_mbox_post(sys_mbox_t *mbox, void *msg) {
    auto m = static_cast<sys_mbox_internal*>(*mbox);
    if (!m || !m->is_valid) return;
    std::lock_guard<std::mutex> lock(m->mtx);
    m->q.push(msg);
    m->cv.notify_one();
}

err_t sys_mbox_trypost(sys_mbox_t *mbox, void *msg) {
    auto m = static_cast<sys_mbox_internal*>(*mbox);
    if (!m || !m->is_valid) return ERR_MEM;
    std::lock_guard<std::mutex> lock(m->mtx);
    if (m->size > 0 && m->q.size() >= m->size) {
        return ERR_MEM; // Full
    }
    m->q.push(msg);
    m->cv.notify_one();
    return ERR_OK;
}

err_t sys_mbox_trypost_fromisr(sys_mbox_t *mbox, void *msg) {
    return sys_mbox_trypost(mbox, msg);
}

u32_t sys_arch_mbox_fetch(sys_mbox_t *mbox, void **msg, u32_t timeout) {
    auto m = static_cast<sys_mbox_internal*>(*mbox);
    if (!m || !m->is_valid) return SYS_ARCH_TIMEOUT;
    
    std::unique_lock<std::mutex> lock(m->mtx);
    
    if (timeout == 0) {
        auto start = sys_now();
        m->cv.wait(lock, [&]{ return !m->q.empty() || !m->is_valid; });
        if (!m->is_valid) return SYS_ARCH_TIMEOUT;
        if (msg) *msg = m->q.front();
        m->q.pop();
        return sys_now() - start;
    } else {
        auto start = sys_now();
        bool success = m->cv.wait_for(lock, std::chrono::milliseconds(timeout), [&]{ return !m->q.empty() || !m->is_valid; });
        if (!success || !m->is_valid) return SYS_ARCH_TIMEOUT;
        if (msg) *msg = m->q.front();
        m->q.pop();
        return sys_now() - start;
    }
}

u32_t sys_arch_mbox_tryfetch(sys_mbox_t *mbox, void **msg) {
    auto m = static_cast<sys_mbox_internal*>(*mbox);
    if (!m || !m->is_valid) return SYS_MBOX_EMPTY;
    std::lock_guard<std::mutex> lock(m->mtx);
    if (m->q.empty()) return SYS_MBOX_EMPTY;
    if (msg) *msg = m->q.front();
    m->q.pop();
    return 0;
}

void sys_mbox_free(sys_mbox_t *mbox) {
    auto m = static_cast<sys_mbox_internal*>(*mbox);
    if (!m || !m->is_valid) return;
    {
        std::lock_guard<std::mutex> lock(m->mtx);
        m->is_valid = false;
        m->cv.notify_all();
    }
    delete m;
    *mbox = nullptr;
}

int sys_mbox_valid(sys_mbox_t *mbox) {
    if (!mbox) return 0;
    auto m = static_cast<sys_mbox_internal*>(*mbox);
    return m && m->is_valid;
}

void sys_mbox_set_invalid(sys_mbox_t *mbox) {
    if (mbox && *mbox) {
        auto m = static_cast<sys_mbox_internal*>(*mbox);
        m->is_valid = false;
    }
}

// -- Threads --

extern "C" {

sys_thread_t sys_thread_new(const char *name, lwip_thread_fn thread, void *arg, int stacksize, int prio) {
    auto t = new std::thread(thread, arg);
    t->detach();
    return static_cast<void*>(t);
}

} // extern "C"
