package com.winlator.core;

import android.content.Context;

import com.winlator.core.envvars.EnvVars;
import com.winlator.xenvironment.ImageFs;

public class DXVKHelper {
    public static final String DEFAULT_CONFIG = "version="+DefaultVersion.DXVK+",framerate=0,maxDeviceMemory=0";

    public static KeyValueSet parseConfig(Object config) {
        String data = config != null && !config.toString().isEmpty() ? config.toString() : DEFAULT_CONFIG;
        return new KeyValueSet(data);
    }

    public static void setEnvVars(Context context, KeyValueSet config, EnvVars envVars) {
        ImageFs imageFs = ImageFs.find(context);
        envVars.put("DXVK_STATE_CACHE_PATH", imageFs.cache_path);
        envVars.put("DXVK_LOG_LEVEL", "none");

        // DXVK defaults to one shader compiler thread per core. A cores/2 cap was tried and made
        // things measurably worse: a device log showed the ANR-triggering stall happening while
        // device-wide CPU was actually LOW (~30%, cores mostly idle) — the render thread was
        // blocked waiting on a *synchronous* first-use pipeline compile (DXVK_ASYNC only smooths
        // over recompiles of an already-existing pipeline, not the first one a given draw needs),
        // and fewer compiler threads just makes that specific blocking wait take longer. Cutting
        // parallelism trades a shorter, higher CPU% window for a longer, lower CPU% stall — the
        // wrong trade here, since the stall itself is what trips the 5s ANR watchdog. cores-2
        // (still capped, not stock) was the only config tested so far that avoided both a hard
        // ANR-driven kill (stock/uncapped) and an extended multi-ANR stall (cores/2).
        int cpuCores = Runtime.getRuntime().availableProcessors();
        if (cpuCores > 2) {
            envVars.put("DXVK_NUM_COMPILER_THREADS", String.valueOf(cpuCores - 2));
        }

        StringBuilder content = new StringBuilder("\"");
        String maxDeviceMemory = config.get("maxDeviceMemory");
        if (!maxDeviceMemory.isEmpty() && !maxDeviceMemory.equals("0")) {
            content.append("dxgi.maxDeviceMemory = ").append(maxDeviceMemory).append("\n");
            content.append("dxgi.maxSharedMemory = ").append(maxDeviceMemory).append("\n");
        }

        String maxFeatureLevel = config.get("maxFeatureLevel");
        if (!maxFeatureLevel.isEmpty() && !maxFeatureLevel.equals("0")) {
            content.append("d3d11.maxFeatureLevel = ").append(maxFeatureLevel).append("\n");
            envVars.put("DXVK_FEATURE_LEVEL", maxFeatureLevel);
        }

        String framerate = config.get("framerate");
        if (!framerate.isEmpty() && !framerate.equals("0")) {
            envVars.put("DXVK_FRAME_RATE", framerate);
        }
        String customDevice = config.get("customDevice");
        if (customDevice.contains(":")) {
            String[] parts = customDevice.split(":");
            content.append("dxgi.customDeviceId = ").append(parts[0]).append("\n")
                   .append("dxgi.customVendorId = ").append(parts[1]).append("\n")
                   .append("d3d9.customDeviceId = ").append(parts[0]).append("\n")
                   .append("d3d9.customVendorId = ").append(parts[1]).append("\n")
                   .append("dxgi.customDeviceDesc = \"").append(parts[2]).append("\"\n")
                   .append("d3d9.customDeviceDesc = \"").append(parts[2]).append("\"\n");
        }
        if (config.getBoolean("constantBufferRangeCheck")) {
            content.append("d3d11.constantBufferRangeCheck = \"True\"\n");
        }

        String async = config.get("async");
        if (!async.isEmpty() && !async.equals("0"))
            envVars.put("DXVK_ASYNC", "1");

        String asyncCache = config.get("asyncCache");
        if (!asyncCache.isEmpty() && !asyncCache.equals("0"))
            envVars.put("DXVK_GPLASYNCCACHE", "1");
        content.append('"');

        envVars.put("DXVK_CONFIG_FILE", imageFs.getRootDir() + ImageFs.CONFIG_PATH + "/dxvk.conf");
        envVars.put("DXVK_CONFIG", content.toString());
    }

    public static void setVKD3DEnvVars(Context context, KeyValueSet config, EnvVars envVars) {
        String featureLevel = config.get("vkd3dFeatureLevel", "12_1");
        envVars.put("VKD3D_FEATURE_LEVEL", featureLevel);
    }
}
