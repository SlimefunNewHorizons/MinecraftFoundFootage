# Voice cloning natives (Windows x64)

`src/main/resources/natives/windows-x64/*.dll` is [omnivoice.cpp](https://github.com/ServeurpersoCom/omnivoice.cpp) (MIT)
at commit `1a468d8`, with `spb.patch` applied, built by `build-windows.cmd`. `VoiceCloner` loads it through JNA.

What the patch adds:

- `src/spb-voice.cpp`: flat C bridge (`spb_*`) so Java only passes primitives and arrays.
- CFG off: with guidance 0 the unconditional batch row is dropped, so every MaskGIT step costs one forward instead of two.
- `tools/spb-voice-test.cpp`: runs the bridge like the mod does, to benchmark a device.
- `OV_THREADS` env override of the CPU thread count (benchmarking only).

## Rebuild

Needs Visual Studio 2022 Build Tools, LLVM (clang-cl), the Vulkan SDK, CMake and Ninja.

```bat
git clone --recurse-submodules https://github.com/ServeurpersoCom/omnivoice.cpp
cd omnivoice.cpp && git checkout 1a468d8 && git submodule update && git apply ..\spb.patch && cd ..
build-windows.cmd
```

Then copy `spb-voice.dll`, `ggml.dll`, `ggml-base.dll`, `ggml-cpu.dll` and `ggml-vulkan.dll` from `omnivoice.cpp/bs/`.

Build choices:

- clang-cl: about 11% faster than MSVC on the CPU.
- AVX2 baseline: the all-CPU-variants build fails under clang-cl. `VoiceCloner` checks AVX2 before loading.
- No OpenMP: clang-cl would link `libomp140.x86_64.dll`, which players do not have.
- Backends load at runtime (`GGML_BACKEND_DL`): without a Vulkan driver only the CPU backend loads.
- Static CRT (`/MT`): the Java runtimes Minecraft ships carry their own, older `msvcp140.dll`
  (14.27 in java-runtime-gamma). Code built with a newer toolset crashes with it in `std::mutex`,
  so the DLLs must not import the VC++ runtime at all.
