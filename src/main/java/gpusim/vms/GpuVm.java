package gpusim.vms;

import org.cloudsimplus.vms.Vm;
import gpusim.vgpu.VGpu;

public interface GpuVm extends Vm {

    GpuVm NULL = new GpuVmNull();

    void setType(String type);

    String getType();

    GpuVm setVGpu(VGpu vgpu);

    VGpu getVGpu();

    boolean hasVGpu();
}