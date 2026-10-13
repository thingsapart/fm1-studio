include("${CMAKE_CURRENT_LIST_DIR}/../build/paths.cmake")
set(H "${KW_ROOT}/firmware/p4/src")
idf_component_register(SRCS main.cpp keyboard.cpp surface.cpp storage.cpp platform.cpp
    ../engine/core.c
    "${H}/board_services.cpp" "${H}/panel.cpp" "${H}/audio_device.cpp"
    "${H}/audio_preferences.cpp" "${H}/esp32_keyboard.cpp"
    "${H}/fonts/kw_space_20.c"
    INCLUDE_DIRS . ../engine ../build/gen ../build/gen/sloop_firmware "${H}" "${KW_ROOT}/firmware/display/contrib/lvgl_ui"
    REQUIRES lvgl esp_timer esp_psram esp_driver_i2c esp_driver_i2s esp_driver_ppa
      esp_driver_gpio esp_driver_sdmmc esp_driver_uart esp_app_format esp_lcd esp_hw_support esp_mm
      fatfs wear_levelling esp_partition nvs_flash usb json mbedtls esp_driver_usb_serial_jtag
      espressif__usb_host_uac espressif__esp_codec_dev)
target_compile_features(${COMPONENT_LIB} PRIVATE cxx_std_17)
target_compile_definitions(${COMPONENT_LIB} PRIVATE KW_P4=1 KW_UI_DEV=0 KW_RENDER_PROFILE=0
    KW_RENDER_BACKBUFFER=1 KW_RENDER_BASELINE=0 KW_RENDER_PPA=0 KW_RENDER_INTERNAL=0
    KW_RENDER_LINES=24 KW_RENDER_MODE=LV_DISPLAY_RENDER_MODE_DIRECT)
target_compile_options(${COMPONENT_LIB} PRIVATE -O2 -fwrapv -Wno-int-to-pointer-cast)
idf_component_get_property(USB_LIB usb COMPONENT_LIB)
get_target_property(USB_SOURCES ${USB_LIB} SOURCES)
set(USB_HELPERS "${IDF_PATH}/components/usb/usb_helpers.c")
set(USB_PATCHED "${CMAKE_CURRENT_BINARY_DIR}/usb_helpers_p4.c")
execute_process(COMMAND "${PYTHON}" "${KW_ROOT}/firmware/p4/tools/patch_usb_descriptors.py"
    "${USB_HELPERS}" "${USB_PATCHED}" RESULT_VARIABLE PATCH_RESULT)
if(NOT PATCH_RESULT EQUAL 0)
    message(FATAL_ERROR "Pinned USB descriptor correction failed")
endif()
list(REMOVE_ITEM USB_SOURCES "${USB_HELPERS}")
list(APPEND USB_SOURCES "${USB_PATCHED}")
set_property(TARGET ${USB_LIB} PROPERTY SOURCES "${USB_SOURCES}")
