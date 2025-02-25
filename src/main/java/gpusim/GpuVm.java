package gpusim;

import java.util.List;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

public class GpuVm extends VmSimple {
    public static final GpuVm NULL = new GpuVm();
    private List<GpuCloudlet> cloudletList;

    public GpuVm(final int id, final long mipsCapacity, final int pesNumber) {
        super(id, mipsCapacity, pesNumber);
    }

    public GpuVm(final long mipsCapacity, final int pesNumber) {
        super(mipsCapacity, pesNumber);
    }

    public GpuVm() {
        this(-1, 0, 1);
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
