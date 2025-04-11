package gpusim.provisioners;

import gpusim.resources.GpuCore;
import gpusim.vgpu.VGpu;

public interface CoreProvisioner extends GpuResourceProvisioner {

    CoreProvisioner NULL = new CoreProvisionerNull();

    void setCore(GpuCore core);

    @Override
    boolean allocateResourceForVGpu(VGpu vgpu, long mipsCapacity);

    @Override
    long getAllocatedResourceForVGpu(VGpu vgpu);

    @Override
    long deallocateResourceForVGpu(VGpu vgpu);

    @Override
    long getTotalAllocatedResource();

    double getUtilization();
}