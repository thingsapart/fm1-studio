/* SPDX-License-Identifier: GPL-3.0-only */
#include "registry.h"
#include "board_services.h"
#include "panel.h"
#include "audio_device.h"
#include "audio_preferences.h"
#include "esp32_keyboard.h"
#include "storage_files.h"
#include "esp_heap_caps.h"
#include "esp_timer.h"
#include "esp_log.h"
#include "esp_system.h"
#include "nvs_flash.h"
#include "nvs.h"
#include "freertos/task.h"
#include "freertos/semphr.h"
#include <atomic>
#include <algorithm>
#include <cstring>
#include <string>
#include <cstdio>

namespace {
constexpr int SAVE=100, SWITCH=101, RELEASE_TOUCH=102;
struct Event {int kind,id,value,source;};
QueueHandle_t commands;
SemaphoreHandle_t exchange;
std::atomic<bool> overflow{false};
std::atomic<int> wheel{0},saveState{0},nextFirmware{-1};
std::atomic<const char*> notice{"Starting"};
const fm1_definition *definition;
const fm1_firmware *engine;
uint32_t *pending;
uint8_t *snapshot;
bool fresh=false,mounted=false;
host::ImageFiles *images;
std::string pathA,pathB,pathTemp;

void send(int kind,int id,int value,int source=0) {
    Event e{kind,id,value,source};
    if(xQueueSend(commands,&e,0)!=pdTRUE)overflow=true;
}
void store(void*) {
    for(;;) {
        if(saveState==2) {
            bool ok=images->commit(snapshot);notice=ok?"Saved to SD":"SD save failed";
            int next=nextFirmware.load();
            if(ok&&next>=0) {
                nvs_handle_t nvs;const auto *d=fm1_firmware_at(next);
                esp_err_t rc=nvs_open("fm1_wrapper",NVS_READWRITE,&nvs);
                if(rc==ESP_OK) {rc=nvs_set_str(nvs,"firmware",d->id);if(rc==ESP_OK)rc=nvs_commit(nvs);nvs_close(nvs);}
                if(rc==ESP_OK)esp_restart();
                notice="Firmware preference save failed";
            }
            nextFirmware=-1;saveState=0;
        }
        vTaskDelay(pdMS_TO_TICKS(20));
    }
}
bool checkpoint(int next) {
    if(!mounted){notice="SD card required to save / switch";return false;}
    if(saveState!=0){notice="Save in progress";return false;}
    if(engine->playing()){notice="Stop playback before save / switch";return false;}
    saveState=1;engine->event(FM1_PANIC,0,0);engine->event(FM1_STOP,0,0);
    float tail[512];engine->render(tail,256);
    if(!engine->checkpoint()){saveState=0;notice="Firmware checkpoint rejected";return true;}
    std::memcpy(snapshot,engine->storage(),engine->storage_size);
    nextFirmware=next;notice=next>=0?"Saving before firmware restart":"Saving to SD";saveState=2;
    return true;
}
void run(void*) {
    bool restored=mounted&&images->load(snapshot);engine->init(restored?snapshot:nullptr);
    notice=mounted?"SD ready":"SD unavailable";
    float pcm[256];int16_t output[256];bool held[2][41]{};
    int64_t lastUI=0,lastReport=0;unsigned peak=0,overruns=0;
    auto *frame=static_cast<uint32_t*>(heap_caps_malloc(240*240*4,MALLOC_CAP_SPIRAM));if(!frame)abort();
    for(;;) {
        auto start=esp_timer_get_time();
        if(overflow.exchange(false)){xQueueReset(commands);std::memset(held,0,sizeof held);engine->event(FM1_PANIC,0,0);}
        Event e;for(unsigned i=0;i<128&&xQueueReceive(commands,&e,0)==pdTRUE;i++) {
            if(e.kind==SAVE){if(checkpoint(-1))std::memset(held,0,sizeof held);continue;}
            if(e.kind==SWITCH){if(fm1_firmware_at(e.id)!=definition&&checkpoint(e.id))std::memset(held,0,sizeof held);continue;}
            if(e.kind==RELEASE_TOUCH){
                for(int n=0;n<41;n++)if(held[0][n]){held[0][n]=false;if(!held[1][n])engine->event(n<14?FM1_BUTTON:FM1_KEY,n<14?n:n-14,0);}
                continue;
            }
            if(nextFirmware>=0)continue;
            if(e.kind==FM1_BUTTON||e.kind==FM1_KEY) {
                int index=e.kind==FM1_BUTTON?e.id:14+e.id;
                if(index<0||index>=41||e.source<0||e.source>1)continue;
                bool before=held[0][index]||held[1][index];held[e.source][index]=e.value!=0;
                bool after=held[0][index]||held[1][index];if(before==after)continue;e.value=after;
            }
            if(e.kind==FM1_PANIC)std::memset(held,0,sizeof held);
            engine->event(e.kind,e.id,e.value);
        }
        if(nextFirmware>=0)std::fill_n(pcm,256,0.0f);else engine->render(pcm,128);
        for(unsigned i=0;i<256;i++)output[i]=int16_t(std::clamp(pcm[i],-1.0f,1.0f)*32767);
        auto now=esp_timer_get_time();
        if(now-lastUI>=40000) {
            engine->tick(uint32_t(now/1000));engine->pixels(frame);
            if(xSemaphoreTake(exchange,0)==pdTRUE){std::memcpy(pending,frame,240*240*4);fresh=true;xSemaphoreGive(exchange);}
            lastUI=now;
        }
        unsigned elapsed=esp_timer_get_time()-start;peak=std::max(peak,elapsed);if(elapsed>2902)overruns++;
        if(now-lastReport>=1000000){if(overruns)ESP_LOGW("FM1","%s service peak %u us, over-period %u",definition->id,peak,overruns);peak=overruns=0;lastReport=now;}
        p4_audio->submit(output,128);
    }
}

lv_obj_t *controlArea,*canvas;
int dragY=0;const fm1_control *drag=nullptr;
static const int WHITE[]={0,2,4,6,7,9,11,12,14,16,18,19,21,23,24,26};
static const int BLACK[]={1,3,5,8,10,13,15,17,20,22,25};
void control(lv_event_t *event) {
    auto code=lv_event_get_code(event);
    auto *c=static_cast<const fm1_control*>(lv_event_get_user_data(event));
    if(c->kind==FM1_ENCODER) {
        if(code==LV_EVENT_PRESSED){lv_point_t p;lv_indev_get_point(lv_indev_active(),&p);drag=c;dragY=p.y;wheel=c->target;}
        else if(code==LV_EVENT_PRESSING&&drag==c){lv_point_t p;lv_indev_get_point(lv_indev_active(),&p);int delta=(dragY-p.y)/6;if(delta){send(c->kind,c->target,delta);dragY-=delta*6;}}
        else if(code==LV_EVENT_RELEASED||code==LV_EVENT_PRESS_LOST)drag=nullptr;
    } else if(code==LV_EVENT_PRESSED)send(c->kind,c->target,1);
    else if(code==LV_EVENT_RELEASED||code==LV_EVENT_PRESS_LOST)send(c->kind,c->target,0);
}
lv_obj_t *tile(lv_obj_t *parent,const char *text,int x,int y,int w,int h,const fm1_control *c=nullptr) {
    auto *button=lv_button_create(parent);lv_obj_set_pos(button,x,y);lv_obj_set_size(button,w,h);
    lv_obj_set_style_pad_all(button,2,0);lv_obj_add_flag(button,LV_OBJ_FLAG_PRESS_LOCK);
    lv_obj_set_style_text_font(button,&lv_font_montserrat_14,0);
    auto *label=lv_label_create(button);lv_label_set_text(label,text);lv_obj_center(label);
    if(c)lv_obj_add_event_cb(button,control,LV_EVENT_ALL,const_cast<fm1_control*>(c));
    return button;
}
void screen(unsigned index,bool navigate) {
    if(index>=definition->screen_count)return;
    send(RELEASE_TOUCH,0,0);drag=nullptr;lv_obj_clean(controlArea);
    const auto &s=definition->screens[index];
    for(unsigned i=0;i<s.count;i++)tile(controlArea,s.controls[i].label,(i%4)*106,(i/4)*54,100,48,&s.controls[i]);
    if(navigate)for(unsigned i=0;i<s.enter_count;i++)send(s.enter[i].kind,s.enter[i].target,s.enter[i].value);
}
void ui(void*) {
    lv_init();lv_display_t *display=nullptr;lv_indev_t *input=nullptr;ESP_ERROR_CHECK(p4::panel_init(&display,&input));
    lv_obj_set_style_bg_color(lv_screen_active(),lv_color_hex(0x101216),0);
    auto *rgb=static_cast<uint16_t*>(heap_caps_malloc(360*360*2,MALLOC_CAP_SPIRAM));if(!rgb)abort();
    canvas=lv_canvas_create(lv_screen_active());lv_obj_set_pos(canvas,0,56);lv_canvas_set_buffer(canvas,rgb,360,360,LV_COLOR_FORMAT_RGB565);
    controlArea=lv_obj_create(lv_screen_active());lv_obj_set_pos(controlArea,370,56);lv_obj_set_size(controlArea,430,360);lv_obj_set_style_pad_all(controlArea,0,0);
    auto *picker=lv_dropdown_create(lv_screen_active());lv_obj_set_pos(picker,370,4);lv_obj_set_size(picker,200,44);
    std::string options;for(unsigned i=0;i<definition->screen_count;i++){if(i)options+='\n';options+=definition->screens[i].title;}
    lv_dropdown_set_options(picker,options.c_str());
    lv_obj_add_event_cb(picker,[](lv_event_t *e){screen(lv_dropdown_get_selected(static_cast<lv_obj_t*>(lv_event_get_target(e))),true);},LV_EVENT_VALUE_CHANGED,nullptr);
    auto *firmwares=lv_dropdown_create(lv_screen_active());lv_obj_set_pos(firmwares,576,4);lv_obj_set_size(firmwares,220,44);
    options.clear();unsigned selected=0;
    for(unsigned i=0;i<fm1_firmware_count();i++){if(i)options+='\n';options+=fm1_firmware_at(i)->name;if(fm1_firmware_at(i)==definition)selected=i;}
    lv_dropdown_set_options(firmwares,options.c_str());lv_dropdown_set_selected(firmwares,selected);
    lv_obj_add_event_cb(firmwares,[](lv_event_t *e){send(SWITCH,lv_dropdown_get_selected(static_cast<lv_obj_t*>(lv_event_get_target(e))),0);},LV_EVENT_VALUE_CHANGED,nullptr);
    auto *save=tile(lv_screen_active(),"SD SAVE",260,4,100,44);lv_obj_add_event_cb(save,[](lv_event_t*){send(SAVE,0,0);},LV_EVENT_CLICKED,nullptr);
    auto *status=lv_label_create(lv_screen_active());lv_obj_set_pos(status,4,8);lv_obj_set_width(status,248);lv_obj_set_style_text_color(status,lv_color_hex(0xffffff),0);
    static fm1_control keys[27];
    for(int i=0;i<16;i++){keys[WHITE[i]]={"",FM1_KEY,WHITE[i]};char label[8];std::snprintf(label,sizeof label,"%d",i+1);tile(lv_screen_active(),label,i*50,424,48,56,&keys[WHITE[i]]);}
    for(int k:BLACK){int i=0;while(i<16&&WHITE[i]<k)i++;keys[k]={"",FM1_KEY,k};auto *b=tile(lv_screen_active(),"",i*50-16,424,32,32,&keys[k]);lv_obj_set_style_bg_color(b,lv_color_hex(0x151820),0);}
    screen(0,false);
    for(;;){
        if(xSemaphoreTake(exchange,0)==pdTRUE){if(fresh){for(unsigned y=0;y<360;y++)for(unsigned x=0;x<360;x++){
            uint32_t p=pending[(y*240/360)*240+x*240/360];rgb[y*360+x]=((p>>19)&31)<<11|((p>>10)&63)<<5|((p>>3)&31);
        }fresh=false;lv_obj_invalidate(canvas);}xSemaphoreGive(exchange);}
        lv_dropdown_set_selected(firmwares,selected);lv_label_set_text(status,notice.load());p4::panel_service();if(p4::panel_render_ready())lv_timer_handler();vTaskDelay(pdMS_TO_TICKS(5));
    }
}
void keyboard(void*) {
    klickwerk_keyboard::Keyboard keyboard;ESP_ERROR_CHECK(keyboard.begin(p4::i2c_bus));
    static bool fn=false;static int kind[30]{},target[30]{};static bool down[30]{};
    klickwerk_keyboard::Sink sink;
    sink.event=[](const klickwerk_keyboard::Event &e,void*) {
        if(e.kind==KW_ENCODER){send(FM1_ENCODER,wheel.load(),e.value,1);return true;}
        if(e.kind!=KW_KEY||e.id>=30)return true;
        unsigned id=e.id;if(id==29){fn=e.value!=0;return true;}
        if(e.value&& !down[id]) {
            down[id]=true;kind[id]=-1;
            if(fn&&id<6){const int targets[]={3,4,5,6,0,2};wheel=targets[id];return true;}
            if(fn&&id==10){wheel=1;return true;}
            if(id>=6&&id%6<4){unsigned pad=(id/6-1)*4+id%6;if(fn&&pad>=11)return true;kind[id]=FM1_KEY;target[id]=fn?BLACK[pad]:WHITE[pad];}
            else {const int buttons[30]={0,1,2,3,4,5,-1,-1,-1,-1,6,10,-1,-1,-1,-1,7,11,-1,-1,-1,-1,8,9,-1,-1,-1,-1,12,-1};kind[id]=FM1_BUTTON;target[id]=buttons[id];if(id==28&&fn)target[id]=13;}
            if(kind[id]>=0&&target[id]>=0)send(kind[id],target[id],1,1);
        } else if(!e.value&&down[id]){down[id]=false;if(kind[id]>=0&&target[id]>=0)send(kind[id],target[id],0,1);}
        return true;
    };
    sink.reset=[](const klickwerk_keyboard::Status&,void*){fn=false;std::fill_n(down,30,false);send(FM1_PANIC,0,0,1);return true;};
    int64_t lastGood=esp_timer_get_time();bool lost=false;
    for(;;){if(keyboard.poll(sink)==ESP_OK){lastGood=esp_timer_get_time();lost=false;}else if(!lost&&esp_timer_get_time()-lastGood>250000){sink.reset(keyboard.status(),nullptr);keyboard.disconnected();lost=true;}
        vTaskDelay(pdMS_TO_TICKS(lost?100:2));}
}
}

extern "C" void app_main() {
    ESP_ERROR_CHECK(nvs_flash_init());char id[64]={};size_t size=sizeof id;nvs_handle_t nvs;
    if(nvs_open("fm1_wrapper",NVS_READONLY,&nvs)==ESP_OK){nvs_get_str(nvs,"firmware",id,&size);nvs_close(nvs);}
    definition=fm1_firmware_find(id);if(!definition)definition=fm1_firmware_at(0);engine=definition->entry();
    if(!engine||engine->abi!=FM1_ABI||engine->struct_size!=sizeof(fm1_firmware)||
       engine->sample_rate!=44100||engine->width!=240||engine->height!=240||
       engine->storage_size!=host::image::size||std::strcmp(engine->id,definition->id))abort();
    ESP_ERROR_CHECK(p4::hardware_init());p4::load_audio_preferences();
    commands=xQueueCreate(256,sizeof(Event));exchange=xSemaphoreCreateMutex();
    pending=static_cast<uint32_t*>(heap_caps_calloc(240*240,4,MALLOC_CAP_SPIRAM));
    snapshot=static_cast<uint8_t*>(heap_caps_malloc(engine->storage_size,MALLOC_CAP_SPIRAM));
    if(!commands||!exchange||!pending||!snapshot)abort();
    mounted=p4::mount_sd();pathA=std::string("/sdcard/fm1-")+definition->id+"-a.bin";pathB=std::string("/sdcard/fm1-")+definition->id+"-b.bin";pathTemp=std::string("/sdcard/fm1-")+definition->id+".tmp";
    images=new host::ImageFiles(pathA.c_str(),pathB.c_str(),pathTemp.c_str());
    p4_audio=new P4Audio;ESP_ERROR_CHECK(p4_audio->init());p4_audio->set_volume(unsigned(p4::preferred_master_volume()*100));p4_audio->set_route(P4Audio::Route(p4::preferred_output()));
    if(xTaskCreatePinnedToCore(store,"fm1-store",4096,nullptr,2,nullptr,0)!=pdPASS)abort();
    if(xTaskCreatePinnedToCore(ui,"fm1-ui",12288,nullptr,3,nullptr,0)!=pdPASS)abort();
    if(xTaskCreatePinnedToCore(keyboard,"fm1-keys",4096,nullptr,4,nullptr,0)!=pdPASS)abort();
    if(xTaskCreatePinnedToCore(run,"fm1-engine",24576,nullptr,16,nullptr,1)!=pdPASS)abort();
}
