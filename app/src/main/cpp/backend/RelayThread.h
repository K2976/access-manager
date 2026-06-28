#ifndef ACCESSMANAGER_RELAYTHREAD_H
#define ACCESSMANAGER_RELAYTHREAD_H

#include <thread>
#include <vector>
#include <mutex>
#include <poll.h>
#include "arch/sys_arch.h"

namespace dev {
namespace kartik {
namespace accessmanager {
namespace backend {

enum class RelayCmd : uint8_t {
    ADD_FD,
    REMOVE_FD,
    RESUME_IN,
    ADD_POLLOUT,
    REMOVE_POLLOUT,
    STOP
};

struct RelayControl {
    RelayCmd cmd;
    int fd;
};

class RelayThread {
public:
    // target_mbox is the mailbox of the LwipBackend to post POSIX_READY events to.
    RelayThread(sys_mbox_t* target_mbox);
    ~RelayThread();

    bool start();
    void stop();

    // These methods are called by LwipBackend (or other threads)
    void addFd(int fd);
    void removeFd(int fd);
    void resumePollIn(int fd); // Unmask POLLIN after reading EAGAIN
    void addPollOut(int fd);
    void removePollOut(int fd);

private:
    void threadLoop();
    void sendControl(RelayCmd cmd, int fd);
    void processControl();

    sys_mbox_t* mbox;
    std::thread* worker;
    bool is_running;
    
    int ctrl_pipe[2];
    std::vector<pollfd> poll_fds;
    
    // To ensure fast indexing, we can just rebuild poll_fds on ADD/REMOVE
    // or keep a parallel vector. Since Max FDs is ~1000, rebuild is very fast.
    std::vector<int> active_fds;
    void rebuildPollFds();
};

} // namespace backend
} // namespace accessmanager
} // namespace kartik
} // namespace dev

#endif // ACCESSMANAGER_RELAYTHREAD_H
