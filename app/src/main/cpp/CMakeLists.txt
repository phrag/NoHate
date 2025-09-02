cmake_minimum_required(VERSION 3.22.1)
project(llamabridge)

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp)

add_library(
	llamabridge
	SHARED
	llamabridge.cpp
)

# llama.cpp core
add_subdirectory(${LLAMA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama)

find_library(log-lib log)

# Link with llama library target (llama)
target_link_libraries(
	llamabridge
	llama
	${log-lib}
)

target_compile_features(llamabridge PRIVATE cxx_std_17)

