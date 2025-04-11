package gpusim.listeners;

import org.cloudsimplus.listeners.EventInfo;
import gpusim.vgpu.VGpu;

public interface VGpuEventInfo extends EventInfo {

    VGpu getVGpu();
}