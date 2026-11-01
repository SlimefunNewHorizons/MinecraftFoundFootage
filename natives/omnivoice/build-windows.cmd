@echo off
rem Portable build for the mod: clang, no host-specific flags, ggml backends as
rem loadable modules (Vulkan when present). CPU baseline AVX2: the all-variants build
rem fails under clang-cl (alderlake AVX-VNNI flags), and the mod checks AVX2 before loading.
rem No OpenMP: clang-cl would link libomp140, which players do not have.
rem Static CRT: the Java runtimes Minecraft ships carry an old msvcp140.dll (14.27) that crashes
rem code built with a newer toolset (std::mutex), so the DLLs must not depend on it.
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul
set VULKAN_SDK=C:\VulkanSDK\1.4.363.0
set PATH=%~dp0.venv\Scripts;C:\Program Files\LLVM\bin;%VULKAN_SDK%\Bin;%PATH%
rem Short drive letter: the OneDrive path plus CMake's nested dirs exceed MAX_PATH
subst O: /d >nul 2>&1
subst O: "%~dp0omnivoice.cpp"
cd /d O:\
cmake -S . -B bs -DCMAKE_POLICY_DEFAULT_CMP0091=NEW -DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER=clang-cl -DCMAKE_CXX_COMPILER=clang-cl ^
  -DGGML_NATIVE=OFF -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=OFF -DGGML_AVX=ON -DGGML_AVX2=ON -DGGML_FMA=ON -DGGML_F16C=ON -DGGML_OPENMP=OFF -DGGML_VULKAN=ON
cmake --build bs -j %NUMBER_OF_PROCESSORS%
set RC=%ERRORLEVEL%
cd /d %~dp0
subst O: /d
exit /b %RC%
