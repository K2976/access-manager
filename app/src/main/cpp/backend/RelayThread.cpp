#include "RelayThread.h"
#include "LwipBackend.h" // For BackendEvent definition
#include "common/Logger.h"
#include "lwip/sys.h"
#include <unistd.h>
#include <fcntl.h>
#include <algorithm>
#include <cerrno>

namespace dev {
namespace kartik {
namespace accessmanager {
namespace backend {

RelayThread::RelayThread(sys_mbox_t* target_mbox) : mbox(target_mbox), worker(nullptr), is_running(false) {
    if (pipe(ctrl_pipe) < 0) {
        LOGE("Failed to create RelayThread control pipe");
    } else {
        // Set both ends to non-blocking
        fcntl(ctrl_pipe[0], F_SETFL, O_NONBLOCK);
        fcntl(ctrl_pipe[1], F_SETFL, O_NONBLOCK);
    }
}

RelayThread::~RelayThread() {
    stop();
    if (ctrl_pipe[0] >= 0) { ::close(ctrl_pipe[0]); }
    if (ctrl_pipe[1] >= 0) { ::close(ctrl_pipe[1]); }
}

bool RelayThread::start() {
    if (is_running) return false;
    is_running = true;
    rebuildPollFds(); // Add ctrl_pipe
    worker = new std::thread(&RelayThread::threadLoop, this);
    return true;
}

void RelayThread::stop() {
    if (!is_running) return;
    
    sendControl(RelayCmd::STOP, -1);
    
    if (worker && worker->joinable()) {
        worker->join();
        delete worker;
        worker = nullptr;
    }
    is_running = false;
}

void RelayThread::sendControl(RelayCmd cmd, int fd) {
    if (ctrl_pipe[1] < 0) return;
    RelayControl ctrl{cmd, fd};
    ::write(ctrl_pipe[1], &ctrl, sizeof(ctrl));
}

void RelayThread::addFd(int fd) {
    sendControl(RelayCmd::ADD_FD, fd);
}

void RelayThread::removeFd(int fd) {
    sendControl(RelayCmd::REMOVE_FD, fd);
}

void RelayThread::resumePollIn(int fd) {
    sendControl(RelayCmd::RESUME_IN, fd);
}

void RelayThread::rebuildPollFds() {
    poll_fds.clear();
    // Always poll the control pipe first
    poll_fds.push_back({ctrl_pipe[0], POLLIN, 0});
    
    for (int fd : active_fds) {
        poll_fds.push_back({fd, POLLIN, 0});
    }
}

void RelayThread::processControl() {
    RelayControl ctrl;
    while (::read(ctrl_pipe[0], &ctrl, sizeof(ctrl)) == sizeof(ctrl)) {
        if (ctrl.cmd == RelayCmd::STOP) {
            is_running = false;
            break;
        } else if (ctrl.cmd == RelayCmd::ADD_FD) {
            if (std::find(active_fds.begin(), active_fds.end(), ctrl.fd) == active_fds.end()) {
                active_fds.push_back(ctrl.fd);
                rebuildPollFds();
            }
        } else if (ctrl.cmd == RelayCmd::REMOVE_FD) {
            auto it = std::find(active_fds.begin(), active_fds.end(), ctrl.fd);
            if (it != active_fds.end()) {
                active_fds.erase(it);
                rebuildPollFds();
            }
        } else if (ctrl.cmd == RelayCmd::RESUME_IN) {
            // Find in poll_fds and unmask
            for (auto& p : poll_fds) {
                if (p.fd == ctrl.fd) {
                    p.events |= POLLIN;
                    break;
                }
            }
        }
    }
}

void RelayThread::threadLoop() {
    LOGI("RelayThread started.");
    
    while (is_running) {
        int ret = poll(poll_fds.data(), poll_fds.size(), -1);
        
        if (ret < 0) {
            if (errno == EINTR) continue;
            LOGE("RelayThread poll error: %d", errno);
            break;
        }
        
        if (ret == 0) continue;
        
        // 1. Process control pipe first
        if (poll_fds[0].revents & POLLIN) {
            processControl();
            poll_fds[0].revents = 0;
            if (!is_running) break;
        }
        
        // 2. Process sockets
        for (size_t i = 1; i < poll_fds.size(); ++i) {
            auto& p = poll_fds[i];
            if (p.revents) {
                if (p.revents & POLLIN) {
                    // Mask it out to avoid busy looping while LwipBackend reads
                    p.events &= ~POLLIN;
                    
                    // Post to LwipBackend actor thread
                    BackendEvent* ev = new BackendEvent();
                    ev->type = BackendMessage::POSIX_READY;
                    ev->fd = p.fd;
                    ev->data = nullptr;
                    ev->length = 0;
                    
                    if (sys_mbox_trypost(mbox, ev) != ERR_OK) {
                        delete ev;
                        // If mailbox full, we leave POLLIN masked and hope it recovers?
                        // Actually, if mailbox is full, we must not lose the edge trigger.
                        // Since we are level-triggered, if we re-enable POLLIN it will just trigger again.
                        p.events |= POLLIN;
                    }
                }
                
                if (p.revents & (POLLERR | POLLHUP | POLLNVAL)) {
                    // Socket error/closed
                    BackendEvent* ev = new BackendEvent();
                    ev->type = BackendMessage::POSIX_EOF;
                    ev->fd = p.fd;
                    ev->data = nullptr;
                    ev->length = 0;
                    
                    if (sys_mbox_trypost(mbox, ev) != ERR_OK) {
                        delete ev;
                    }
                    
                    // Mask everything out. LwipBackend will handle closing and call removeFd.
                    p.events = 0;
                }
                
                p.revents = 0;
            }
        }
    }
    
    LOGI("RelayThread exiting.");
}

} // namespace backend
} // namespace accessmanager
} // namespace kartik
} // namespace dev
