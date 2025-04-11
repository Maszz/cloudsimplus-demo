package gpusim.listeners;

import org.cloudsimplus.listeners.EventInfo;
import gpusim.cloudlets.gputasks.GpuTask;

public interface GpuTaskEventInfo extends EventInfo {

    GpuTask getGpuTask();
}