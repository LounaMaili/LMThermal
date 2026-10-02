cmake_minimum_required(VERSION 3.22.1)
project(lmthermal_transport LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(DEPS ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party)
# Mirror upstream android/jni/libusb.mk; retain LGPL library as a separate shared object.
set(USB ${DEPS}/libusb/libusb)
add_library(usb1.0 SHARED
    ${USB}/core.c ${USB}/descriptor.c ${USB}/hotplug.c ${USB}/io.c
    ${USB}/sync.c ${USB}/strerror.c ${USB}/os/linux_usbfs.c
    ${USB}/os/events_posix.c ${USB}/os/threads_posix.c ${USB}/os/linux_netlink.c)
target_include_directories(usb1.0 PUBLIC ${USB} PRIVATE ${DEPS}/libusb/android ${USB}/os)
target_compile_options(usb1.0 PRIVATE -fvisibility=hidden -pthread)
target_link_libraries(usb1.0 PRIVATE log)
set(UVC ${DEPS}/libuvc)
set(libuvc_VERSION_MAJOR 0)
set(libuvc_VERSION_MINOR 0)
set(libuvc_VERSION_PATCH 8)
set(libuvc_VERSION 0.0.8)
configure_file(${UVC}/include/libuvc/libuvc_config.h.in ${CMAKE_CURRENT_BINARY_DIR}/include/libuvc/libuvc_config.h)
# YUYV only: no JPEG decoder or RGB conversion runs in the acquisition path.
add_library(uvc SHARED ${UVC}/src/ctrl.c ${UVC}/src/ctrl-gen.c ${UVC}/src/device.c
    ${UVC}/src/diag.c ${UVC}/src/frame.c ${UVC}/src/init.c ${UVC}/src/stream.c ${UVC}/src/misc.c)
target_include_directories(uvc PUBLIC ${UVC}/include ${CMAKE_CURRENT_BINARY_DIR}/include)
target_link_libraries(uvc PUBLIC usb1.0)
add_library(lmthermal SHARED transport.cpp)
target_link_libraries(lmthermal PRIVATE uvc log)
# NDK 28 defaults to 16 KiB alignment, including every shared dependency.
