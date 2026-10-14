/* SPDX-License-Identifier: GPL-3.0-only */
#include "session.h"
#include "studio_json.h"
#include <aaudio/AAudio.h>
#include <jni.h>
#include <array>
#include <atomic>
#include <chrono>
#include <mutex>
#include <thread>
#include <string>
#include <vector>
#include <deque>
#include <sstream>
#include <memory>
#include <sys/resource.h>

namespace {
Session session;
std::thread worker;
std::atomic<bool> running{false};
std::atomic<int> rate{0}, underruns{0};
std::mutex mutex;
struct Event { int kind, id, value; };
std::vector<Event> queue;
std::array<uint32_t, 240*240> pixels{};
std::string error;
bool overflow = false;
sloop_editor editorState{};
std::deque<uint32_t> midiOutput;
struct DataCommand {int kind,id;std::vector<uint8_t> bytes;};
std::deque<DataCommand> dataQueue;
unsigned dataBytes=0;
void fail(const std::string &message);
void drain(const fm1_firmware *f) {
    std::vector<Event> events;std::deque<DataCommand> data;bool panic;
    {std::lock_guard<std::mutex> lock(mutex);events.swap(queue);data.swap(dataQueue);dataBytes=0;panic=overflow;overflow=false;}
    if(panic)f->event(FM1_PANIC,0,0);
    for(auto e:events)f->event(e.kind,e.id,e.value);
    const auto rich=session.studio();
    for(auto &d:data){int ok=rich&&(d.kind==3?rich->import_sample(d.id,d.bytes.data(),d.bytes.size()):rich->import_object(d.kind,d.id,d.bytes.data(),d.bytes.size()));
        fail(ok?"File loaded":"Import rejected: stop transport and check file");}
}


void fail(const std::string &message) { std::lock_guard<std::mutex> lock(mutex); error = message; }
void run() {
    setpriority(PRIO_PROCESS,0,-16);
    auto f = session.engine();
    if (!f) { running = false; return; }
    AAudioStream *stream = nullptr;
    std::array<float, 512> input{}, output{};
    unsigned inputPos = 256;
    auto next = [&](float *frame) {
        if (inputPos == 256) { f->render(input.data(), 256); inputPos = 0; }
        frame[0] = input[2*inputPos]; frame[1] = input[2*inputPos+1]; inputPos++;
    };
    float a[2]{}, b[2]{};
    double phase = 0;
    static const auto epoch = std::chrono::steady_clock::now();
    uint32_t lastUI = 0, lastSave = 0, lastEditor=0;
    auto published=std::make_unique<sloop_editor>();
    while (running) {
        drain(f);
        uint32_t now = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-epoch).count();
        if (now-lastUI >= 16) {
            f->tick(now);
            std::array<uint32_t, 240*240> frame;
            f->pixels(frame.data());
            { std::lock_guard<std::mutex> lock(mutex); pixels=frame; }
            lastUI=now;
        }
        uint32_t packets[256];unsigned count=f->midi_out(packets,256);
        {std::lock_guard<std::mutex> lock(mutex);if(midiOutput.size()+count>2048){midiOutput.clear();for(int ch=0;ch<16;ch++){midiOutput.push_back(11u|((0xb0u+ch)<<8)|(123u<<16));midiOutput.push_back(11u|((0xb0u+ch)<<8)|(64u<<16));}}for(unsigned i=0;i<count;i++)midiOutput.push_back(packets[i]);}
        if(now-lastEditor>=50&&session.studio()){
            session.studio()->snapshot(published.get());
            {std::lock_guard<std::mutex> lock(mutex);editorState=*published;}
            lastEditor=now;
        }
        if (!f->playing() && now-lastSave >= 2500) {
            try { session.save(); } catch (const std::exception &e) { fail(e.what()); }
            lastSave=now;
        }
        if (!stream) {
            AAudioStreamBuilder *builder = nullptr;
            aaudio_result_t rc = AAudio_createStreamBuilder(&builder);
            if (rc == AAUDIO_OK) {
                AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
                AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
                AAudioStreamBuilder_setChannelCount(builder, 2);
                AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
                rc=AAudioStreamBuilder_openStream(builder, &stream);
                AAudioStreamBuilder_delete(builder);
            }
            if (rc == AAUDIO_OK) {
                rate=AAudioStream_getSampleRate(stream);
                if (rate<8000 || rate>192000) rc=AAUDIO_ERROR_INVALID_RATE;
                else {
                    AAudioStream_setBufferSizeInFrames(stream, 6*AAudioStream_getFramesPerBurst(stream));
                    rc=AAudioStream_requestStart(stream);
                }
            }
            if (rc != AAUDIO_OK) {
                if(stream) AAudioStream_close(stream);
                stream=nullptr; rate=0;
                fail("Audio reconnecting: " + std::to_string(rc));
                std::this_thread::sleep_for(std::chrono::milliseconds(100)); continue;
            }
            next(a); next(b); phase=0;
        }
        double step = double(f->sample_rate)/rate.load();
        for (unsigned i=0; i<256; i++) {
            output[2*i]=a[0]+(b[0]-a[0])*phase;
            output[2*i+1]=a[1]+(b[1]-a[1])*phase;
            phase+=step;
            while(phase>=1) { a[0]=b[0]; a[1]=b[1]; next(b); phase-=1; }
        }
        for(unsigned sent=0; sent<256 && running;) {
            auto count=AAudioStream_write(stream, output.data()+2*sent, 256-sent, 100000000);
            if(count<0) { AAudioStream_close(stream); stream=nullptr; fail("Audio disconnected"); break; }
            sent+=count;
        }
        if(stream) underruns=AAudioStream_getXRunCount(stream);
    }
    if(stream) { AAudioStream_requestStop(stream); AAudioStream_close(stream); }
    drain(f);
    try { session.suspend(); } catch(const std::exception &e) { fail(e.what()); }
    uint32_t packets[256];unsigned count=f->midi_out(packets,256);{std::lock_guard<std::mutex> lock(mutex);for(unsigned i=0;i<count;i++)midiOutput.push_back(packets[i]);}
    rate=0;
}
void stop() {
    running=false;
    if(worker.joinable()) worker.join();
    std::lock_guard<std::mutex> lock(mutex); queue.clear();dataQueue.clear();dataBytes=0;overflow=false;
}
std::string string(JNIEnv *env, jstring text) {
    const char *s=env->GetStringUTFChars(text, nullptr);
    std::string result(s); env->ReleaseStringUTFChars(text,s); return result;
}
}

extern "C" JNIEXPORT jstring JNICALL Java_org_fm1_studio_Native_select(JNIEnv *env,jclass,jstring library,jstring id,jstring directory) {
    stop();
    try {
        session.select(string(env,library),string(env,id),string(env,directory));
        std::array<uint32_t,240*240> frame;
        session.engine()->pixels(frame.data());
        std::lock_guard<std::mutex> lock(mutex); pixels=frame;editorState={};midiOutput.clear();error.clear();underruns=0;
        return nullptr;
    } catch(const std::exception &e) { return env->NewStringUTF(e.what()); }
}
extern "C" JNIEXPORT void JNICALL Java_org_fm1_studio_Native_start(JNIEnv *,jclass) {
    if(running || !session.engine()) return;
    if(worker.joinable()) worker.join();
    running=true; worker=std::thread(run);
}
extern "C" JNIEXPORT void JNICALL Java_org_fm1_studio_Native_stop(JNIEnv *,jclass) { stop(); }
extern "C" JNIEXPORT void JNICALL Java_org_fm1_studio_Native_emit(JNIEnv *,jclass,jint kind,jint id,jint value) {
    if(!running) return;
    std::lock_guard<std::mutex> lock(mutex);
    if(queue.size()>=2048) { queue.clear(); overflow=true; }
    queue.push_back({kind,id,value});
}
extern "C" JNIEXPORT void JNICALL Java_org_fm1_studio_Native_frame(JNIEnv *env,jclass,jintArray out) {
    if(env->GetArrayLength(out)<240*240) return;
    std::lock_guard<std::mutex> lock(mutex);
    env->SetIntArrayRegion(out,0,pixels.size(),reinterpret_cast<const jint *>(pixels.data()));
}
extern "C" JNIEXPORT jstring JNICALL Java_org_fm1_studio_Native_status(JNIEnv *env,jclass) {
    std::lock_guard<std::mutex> lock(mutex);
    std::string text=std::to_string(rate.load())+" Hz · underruns "+std::to_string(underruns.load());
    if(!error.empty()) text+=" · "+error;
    return env->NewStringUTF(text.c_str());
}
extern "C" JNIEXPORT jstring JNICALL Java_org_fm1_studio_Native_editor(JNIEnv *env,jclass){
    auto snapshot=std::make_unique<sloop_editor>();
    {std::lock_guard<std::mutex> lock(mutex);*snapshot=editorState;}
    std::string json=fm1_studio_json(*snapshot);return env->NewStringUTF(json.c_str());
}

static void data(JNIEnv *env,int kind,int id,jbyteArray input){
    int n=env->GetArrayLength(input);if(n<=0||n>81920||!running){fail("Import rejected: audio engine is stopped");return;}
    std::vector<uint8_t> bytes(n);env->GetByteArrayRegion(input,0,n,(jbyte *)bytes.data());
    std::lock_guard<std::mutex> lock(mutex);if(dataQueue.size()>=16||dataBytes+n>1048576){error="Import queue full";return;}dataBytes+=n;dataQueue.push_back({kind,id,std::move(bytes)});
}
extern "C" JNIEXPORT void JNICALL Java_org_fm1_studio_Native_data(JNIEnv *env,jclass,jint kind,jint id,jbyteArray input){data(env,kind,id,input);}
extern "C" JNIEXPORT void JNICALL Java_org_fm1_studio_Native_sample(JNIEnv *env,jclass,jint slot,jbyteArray input){data(env,3,slot,input);}
extern "C" JNIEXPORT jbyteArray JNICALL Java_org_fm1_studio_Native_object(JNIEnv *env,jclass,jint kind,jint id){
    auto rich=session.studio();if(running||!rich)return nullptr;std::vector<uint8_t> bytes(81920);unsigned n=rich->export_object(kind,id,bytes.data(),bytes.size());if(!n)return nullptr;
    jbyteArray result=env->NewByteArray(n);env->SetByteArrayRegion(result,0,n,(jbyte *)bytes.data());return result;
}
extern "C" JNIEXPORT jboolean JNICALL Java_org_fm1_studio_Native_restore(JNIEnv *env,jclass,jbyteArray input){
    if(running||env->GetArrayLength(input)!=1048576)return JNI_FALSE;std::vector<uint8_t> bytes(1048576);env->GetByteArrayRegion(input,0,bytes.size(),(jbyte *)bytes.data());return session.restore(bytes.data(),bytes.size())?JNI_TRUE:JNI_FALSE;
}
extern "C" JNIEXPORT jintArray JNICALL Java_org_fm1_studio_Native_midi(JNIEnv *env,jclass){
    std::lock_guard<std::mutex> lock(mutex);jintArray result=env->NewIntArray(midiOutput.size());std::vector<jint> bytes(midiOutput.begin(),midiOutput.end());if(!bytes.empty())env->SetIntArrayRegion(result,0,bytes.size(),bytes.data());midiOutput.clear();return result;
}
