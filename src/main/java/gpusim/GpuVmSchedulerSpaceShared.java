// package gpusim;

// import java.util.ArrayList;
// import java.util.List;

// import org.cloudsimplus.schedulers.vm.VmSchedulerAbstract;
// import org.cloudsimplus.vms.Vm;

// public class GpuVmSchedulerSpaceShared extends VmSchedulerAbstract {

// /**
// * Creates a space-shared GPU VM scheduler.
// */
// private static final double DEF_VM_MIGRATION_GPU_OVERHEAD = 0.1;

// public GpuVmSchedulerSpaceShared() {
// this(DEF_VM_MIGRATION_GPU_OVERHEAD);
// }

// /**
// * Creates a space-shared GPU VM scheduler, defining a GPU overhead for VM
// * migration.
// *
// * @param vmMigrationGpuOverhead the percentage of Host's GPU usage increase
// * when a
// * VM is migrating in or out of the Host. The
// * value is in scale from 0 to 1 (where 1 is
// * 100%).
// */
// public GpuVmSchedulerSpaceShared(final double vmMigrationGpuOverhead) {
// super(vmMigrationGpuOverhead);
// }

// @Override
// protected boolean isSuitableForVmInternal(final Vm vm, final GpuMipsShare
// requestedMips) {
// final var selectedGpusList =
// getTotalCapacityToBeAllocatedToVm(requestedMips);
// return selectedGpusList.size() >= requestedMips.gpus();
// }

// /**
// * Checks if the requested amount of GPU MIPS is available to be allocated to
// a
// * VM
// *
// * @param requestedMips a list of GPU MIPS requested by a VM
// * @return the list of GPUs that may be allocated to the VM. If the size of
// this
// * list is
// * lower than the size of the requestedMips, it means there aren't
// * enough GPUs
// * with requested MIPS to be allocated to the VM.
// */
// private List<Gpu> getTotalCapacityToBeAllocatedToVm(final GpuMipsShare
// requestedMips) {
// if (((GpuHost) getHost()).getWorkingGpusNumber() < requestedMips.getCores())
// {
// return ((GpuHost) getHost()).getWorkingGpuList();
// }

// final var freeGpuList = getHost().getFreeGpuList();
// final var selectedGpusList = new ArrayList<Gpu>();
// if (freeGpuList.isEmpty()) {
// return selectedGpusList;
// }

// final var gpuIterator = freeGpuList.iterator();
// Gpu gpu = gpuIterator.next();
// for (int i = 0; i < requestedMips.gpus(); i++) {
// if (requestedMips.mips() <= gpu.getCapacity()) {
// selectedGpusList.add(gpu);
// if (!gpuIterator.hasNext()) {
// break;
// }
// gpu = gpuIterator.next();
// }
// }

// return selectedGpusList;
// }

// @Override
// public boolean allocatePesForVmInternal(final Vm vm, final GpuMipsShare
// requestedMips) {
// final var selectedGpusList =
// getTotalCapacityToBeAllocatedToVm(requestedMips);
// if (selectedGpusList.size() < requestedMips.gpus()) {
// return false;
// }

// ((GpuVm) vm).setAllocatedMips(requestedMips);
// return true;
// }

// @Override
// protected long deallocatePesFromVmInternal(final Vm vm, final int
// gpusToRemove) {
// return removeGpusFromVm(vm, ((GpuVm) vm).getAllocatedMips(), gpusToRemove);
// }
// }
