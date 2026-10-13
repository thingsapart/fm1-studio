/* SPDX-License-Identifier: GPL-3.0-only */
// The hardware drivers only need these policy hooks. Standby stays disabled
// until SLOOP activity/tail/shutdown telemetry is qualified on the actual board.
#include "power_manager.h"
#include "ui_dev.h"
namespace p4::power { void activity() {} bool saving() {return false;} }
namespace p4 { bool ui_dev_touch(lv_indev_data_t*) {return false;} }
