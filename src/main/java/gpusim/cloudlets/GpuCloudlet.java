package gpusim.cloudlets;

import org.cloudsimplus.cloudlets.Cloudlet;
import gpusim.cloudlets.gputasks.GpuTask;

public interface GpuCloudlet extends Cloudlet {
    GpuCloudlet NULL = new GpuCloudletNull();

    GpuCloudlet setGpuTask(GpuTask gpuTask);

    GpuTask getGpuTask();
}