# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

# if you want to add some new plugins, add them to librime_jni/rime_jni.cc too
set(RIME_PLUGINS librime-lua librime-octagram librime-predict)

# Windows 无目录符号链接权限时，复制完整内容，避免 COPY_ON_ERROR 只生成空目录。
function(link_rime_directory source destination)
  if(NOT EXISTS "${destination}")
    file(CREATE_LINK "${source}" "${destination}" COPY_ON_ERROR SYMBOLIC)
  endif()
  if(NOT IS_SYMLINK "${destination}")
    file(COPY "${source}/" DESTINATION "${destination}" PATTERN ".git" EXCLUDE)
  endif()
endfunction()

foreach(plugin ${RIME_PLUGINS})
  link_rime_directory("${CMAKE_SOURCE_DIR}/${plugin}"
                      "${CMAKE_SOURCE_DIR}/librime/plugins/${plugin}")
endforeach()

# librime-lua
link_rime_directory("${CMAKE_SOURCE_DIR}/librime-lua-deps"
                    "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/thirdparty")

option(BUILD_TEST "" OFF)
option(BUILD_STATIC "" ON)
add_subdirectory(librime)
target_compile_options(
  rime-static PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=." "-Wno-error=deprecated-declarations")

target_compile_options(
  rime-lua-objs PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")

target_compile_options(
  rime-octagram-objs PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
