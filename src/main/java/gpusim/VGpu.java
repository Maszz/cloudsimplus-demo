package gpusim;

import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.Processor;
import org.cloudsimplus.resources.ResourceManageableAbstract;
import org.cloudsimplus.util.MathUtil;
import org.cloudsimplus.vms.Vm;

public class VGpu extends ResourceManageableAbstract {
    public static final VGpu NULL = new VGpu();

    private final Vm vm;

    /**
     * The individual MIPS of each {@link Pe}.
     */
    private double mips;

    /**
     * Instantiates a Processor for a given VM.
     *
     * @param vm        the {@link Vm} the processor will belong to
     * @param pesNumber number of {@link Pe}s (the processor {@link #getCapacity()
     *                  capacity})
     * @param pesMips   MIPS of each {@link Pe}
     */
    public VGpu(final Vm vm, final long pesNumber, final double pesMips) {
        this(vm, pesNumber);
        setMips(pesMips);
    }

    private VGpu(final Vm vm, final long pesNumber) {
        super(pesNumber, "Unit");
        this.vm = vm;
    }

    private VGpu() {
        super(0, "Unit");
        this.vm = Vm.NULL;
    }

    /**
     * {@return the sum of MIPS} from all {@link Pe}s.
     */
    public double getTotalMips() {
        return getMips() * getCapacity();
    }

    /**
     * Sets the individual MIPS of each {@link Pe}.
     * 
     * @param newMips the new MIPS of each PE
     */
    public void setMips(final double newMips) {
        this.mips = MathUtil.nonNegative(newMips, "MIPS");
    }

    /**
     * {@return the MIPS} of each {@link Pe}.
     */
    public double getMips() {
        return mips;
    }

    /**
     * {@return the number of Pes} of the Processor
     */
    @Override
    public long getCapacity() {
        return super.getCapacity();
    }

    /**
     * Sets the number of {@link Pe}s of the Processor
     * 
     * @param pesNumber the number of PEs to set
     * @return
     */
    @Override
    public boolean setCapacity(long pesNumber) {
        if (pesNumber <= 0) {
            throw new IllegalArgumentException("The Processor's number of PEs must be greater than 0.");
        }
        return super.setCapacity(pesNumber);
    }

    /**
     * {@return the number of available PEs} that are free to be used
     */
    @Override
    public long getAvailableResource() {
        return super.getAvailableResource();
    }

    /**
     * {@return the number of PEs} allocated
     */
    @Override
    public long getAllocatedResource() {
        return super.getAllocatedResource();
    }

}
