/* SPDX-License-Identifier: GPL-3.0-only */
#include "session.h"
#include <dlfcn.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>
#include <cerrno>
#include <stdexcept>
#include <vector>

static void check(const fm1_firmware *f, const std::string &id) {
    if (!f || f->abi != FM1_ABI || f->struct_size != sizeof(fm1_firmware) ||
        !f->id || id != f->id || f->sample_rate < 8000 || f->sample_rate > 192000 ||
        f->width != 240 || f->height != 240 || f->storage_size == 0 ||
        f->storage_size > 16 * 1024 * 1024 || !f->init || !f->render || !f->event ||
        !f->tick || !f->pixels || !f->playing || !f->checkpoint || !f->storage ||
        !f->dirty || !f->clean || !f->midi_out)
        throw std::runtime_error("Incompatible firmware adapter: " + id);
}

Session::~Session() { if (library_) dlclose(library_); }

void Session::select(const std::string &library, const std::string &id, const std::string &directory) {
    if (id.empty() || id.find_first_not_of("abcdefghijklmnopqrstuvwxyz0123456789_") != std::string::npos)
        throw std::runtime_error("Invalid firmware id");
    if (firmware_ && id == firmware_->id) return;
    void *next = dlopen(library.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!next) throw std::runtime_error(dlerror());
    const fm1_firmware *f;
    const fm1_studio_api *studio=nullptr;
    std::string path = directory + "/" + id + ".flash";
    std::vector<uint8_t> image;
    bool found = false;
    try {
        auto entry = reinterpret_cast<fm1_entry>(dlsym(next, "fm1_get_api"));
        if (!entry) throw std::runtime_error("Missing fm1_get_api: " + id);
        f = entry(); check(f, id);
        auto rich=reinterpret_cast<const fm1_studio_api *(*)()>(dlsym(next,"fm1_get_studio_api"));
        studio=rich?rich():nullptr;
        if(studio&&(studio->abi!=FM1_STUDIO_ABI||studio->size!=sizeof(fm1_studio_api)||!studio->snapshot||!studio->import_object||!studio->export_object||!studio->import_sample||!studio->validate_image))throw std::runtime_error("Incompatible instrument editor: "+id);
        image.resize(f->storage_size);
        FILE *in = fopen(path.c_str(), "rb");
        if (in) {
            bool ok = fread(image.data(), 1, image.size(), in) == image.size() && fgetc(in) == EOF && !ferror(in);
            fclose(in);
            if (!ok) throw std::runtime_error("Invalid saved image: " + id);
            found = true;
        } else if (errno != ENOENT) {
            throw std::runtime_error("Cannot read saved image: " + id);
        }
        // Failure to checkpoint the old engine aborts the switch without losing it.
        suspend();
    } catch (...) { dlclose(next); throw; }
    if (library_) dlclose(library_);
    library_ = next; firmware_ = f; path_ = path;
    studio_=studio;
    firmware_->init(found ? image.data() : nullptr);
}

void Session::save() {
    if (!firmware_ || !firmware_->dirty()) return;
    std::string temp = path_ + ".tmp";
    FILE *out = fopen(temp.c_str(), "wb");
    if (!out) throw std::runtime_error("Cannot open firmware save");
    bool ok = fwrite(firmware_->storage(), 1, firmware_->storage_size, out) == firmware_->storage_size;
    if (ok) ok = fflush(out) == 0 && fsync(fileno(out)) == 0;
    if (fclose(out)) ok = false;
    if (!ok || rename(temp.c_str(), path_.c_str())) {
        unlink(temp.c_str()); throw std::runtime_error("Cannot commit firmware save");
    }
    firmware_->clean();
}

void Session::suspend() {
    if (!firmware_) return;
    firmware_->event(FM1_PANIC, 0, 0);
    firmware_->event(FM1_STOP, 0, 0);
    float tail[512]; firmware_->render(tail, 256);
    if(!firmware_->checkpoint()) throw std::runtime_error("Firmware checkpoint rejected");
    save();
}

bool Session::restore(const uint8_t *image,unsigned size) {
    if(!firmware_ || !studio_ || !studio_->validate_image || size!=firmware_->storage_size || !studio_->validate_image(image,size))return false;
    std::string temp=path_+".tmp";
    FILE *out=fopen(temp.c_str(),"wb");if(!out)return false;
    bool ok=fwrite(image,1,size,out)==size;
    if(ok)ok=fflush(out)==0&&fsync(fileno(out))==0;
    if(fclose(out))ok=false;
    if(!ok||rename(temp.c_str(),path_.c_str())){unlink(temp.c_str());return false;}
    firmware_->init(image);return true;
}
