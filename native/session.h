/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
#include "firmware.h"
#include "studio.h"
#include <string>

class Session {
    void *library_ = nullptr;
    const fm1_firmware *firmware_ = nullptr;
    std::string path_;
    const fm1_studio_api *studio_ = nullptr;
public:
    ~Session();
    Session() = default;
    Session(const Session &) = delete;
    Session &operator=(const Session &) = delete;
    void select(const std::string &library, const std::string &id, const std::string &directory);
    void save();
    void suspend();
    bool restore(const uint8_t *image,unsigned size);
    const fm1_studio_api *studio() const { return studio_; }
    const fm1_firmware *engine() const { return firmware_; }
};
