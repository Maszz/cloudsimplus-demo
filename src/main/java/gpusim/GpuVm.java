package gpusim;

import java.util.List;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

public class GpuVm extends VmSimple {
    public static final GpuVm NULL = new GpuVm();
    private List<GpuCloudlet> cloudletList;
    private int vGpuNumber;

    public GpuVm(final int id, final long mipsCapacity, final int pesNumber, final int vGpuNumber) {
        super(id, mipsCapacity, pesNumber);
        this.vGpuNumber = vGpuNumber;

    }

    public GpuVm(final long mipsCapacity, final int pesNumber, final int vGpuNumber) {
        this(-1, mipsCapacity, pesNumber, vGpuNumber);

        // super(mipsCapacity, pesNumber);
    }

    public GpuVm() {
        this(-1, 0, 1, 0);
    }

    public int getVGpuNumber() {
        return vGpuNumber;
    }

    public List<GpuCloudlet> getCloudletList() {
        return cloudletList;
    }

    @Override
    public GpuHost getHost() {
        return (GpuHost) super.getHost();
    }

    @Override
    public Vm setHost(final Host host) {
        if (host == Host.NULL)
            return super.setHost(GpuHost.NULL);

        if (host instanceof GpuHost)
            return super.setHost(host);

        throw new IllegalArgumentException("GpuVm can only be run into a GpuHost");
    }

}
