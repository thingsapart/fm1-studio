# Add this directory to EXTRA_COMPONENT_DIRS in an ESP-IDF board project.
get_filename_component(FM1_PROJECT "${CMAKE_CURRENT_LIST_DIR}/../../.." ABSOLUTE)
if(NOT EXISTS "${FM1_PROJECT}/build/p4/engines.cmake")
    message(FATAL_ERROR "Run python tools/prepare_p4.py before configuring ESP-IDF")
endif()
include("${FM1_PROJECT}/build/p4/engines.cmake")
idf_component_register(SRCS registry.c ${FM1_ENGINE_SOURCES}
    INCLUDE_DIRS . "${FM1_PROJECT}/native" "${FM1_PROJECT}/build/p4"
    REQUIRES esp_psram)
target_compile_options(${COMPONENT_LIB} PRIVATE -O2 -fwrapv -Wno-int-to-pointer-cast)
foreach(ENGINE IN LISTS FM1_ENGINE_IDS)
    set_source_files_properties("${FM1_PROJECT}/build/p4/${ENGINE}/plugin_${ENGINE}.c" PROPERTIES
        INCLUDE_DIRECTORIES "${FM1_PROJECT}/build/p4/${ENGINE}/build/gen;${FM1_PROJECT}/build/p4/${ENGINE}/build/firmware/src")
endforeach()
