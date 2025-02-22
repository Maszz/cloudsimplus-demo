#!/bin/bash

# Ask for the configuration file name
read -p "Enter the name of the config file (default: config.json): " CONFIG_FILE
CONFIG_FILE=${CONFIG_FILE:-config.json}  # Use default if empty

# Ask for the number of datacenters
read -p "Enter number of datacenters (default: 13): " NUM_DATACENTERS
NUM_DATACENTERS=${NUM_DATACENTERS:-13}

# Ask if the user wants to configure Datacenter settings individually
read -p "Do you want to configure each Datacenter cloudlet manually? (Y/N): " CONFIG_DC
CONFIG_DC=${CONFIG_DC:-N}

# Prompt user for each datacenter setting
read -p "Enter SCHEDULING_INTERVAL (default: -1): " SCHEDULING_INTERVAL
SCHEDULING_INTERVAL=${SCHEDULING_INTERVAL:--1}

read -p "Enter number of hosts (default: 2): " HOSTS
HOSTS=${HOSTS:-2}

read -p "Enter HOST_PES (default: 16): " HOST_PES
HOST_PES=${HOST_PES:-16}

read -p "Enter Host RAM_PER_PE (default: 2048): " RAM_PER_PE
RAM_PER_PE=${RAM_PER_PE:-2048}

read -p "Enter HOST_MIPS (default: 38400): " HOST_MIPS
HOST_MIPS=${HOST_MIPS:-38400}

read -p "Enter HOST_BW in Megabits/s (default: 1024): " HOST_BW
HOST_BW=${HOST_BW:-1024}

read -p "Enter HOST_STORAGE in Megabytes (default: 1_000_000): " HOST_STORAGE
HOST_STORAGE=${HOST_STORAGE:-1000000}

read -p "Enter HOST_START_UP_DELAY (default: 5): " HOST_START_UP_DELAY
HOST_START_UP_DELAY=${HOST_START_UP_DELAY:-5}

read -p "Enter HOST_SHUT_DOWN_DELAY (default: 3): " HOST_SHUT_DOWN_DELAY
HOST_SHUT_DOWN_DELAY=${HOST_SHUT_DOWN_DELAY:-3}

read -p "Enter HOST_START_UP_POWER (default: 5): " HOST_START_UP_POWER
HOST_START_UP_POWER=${HOST_START_UP_POWER:-5}

read -p "Enter HOST_SHUT_DOWN_POWER (default: 3): " HOST_SHUT_DOWN_POWER
HOST_SHUT_DOWN_POWER=${HOST_SHUT_DOWN_POWER:-3}

read -p "Enter VMs per host (default: 4): " VM_PER_HOST
VM_PER_HOST=${VM_PER_HOST:-4}

echo "SP: simple"
echo "FF: FirstFit"
echo "BF: BestFit"
echo "RR: RoundRobin"
echo "RD: Random"
read -p "Enter VmAllocationPolicy (SP/FF/BF/RR/RD) (default: BF): " VmAllocationPolicy
VmAllocationPolicy=${VmAllocationPolicy:-BF}

echo "TS: TimeShare"
echo "SS: SpaceShare"
read -p "Enter VmScheduler (TS/SS) (default: TS): " VmScheduler
VmScheduler=${VmScheduler:-TS}

echo "F: Full"
echo "D: Dynamic"
read -p "Enter UtilizationModel (default: D): " UtilizationModel
UtilizationModel=${UtilizationModel:-D}

read -p "Enter CLOUDLET_LENGTH (default: 1000): " CLOUDLET_LENGTH
CLOUDLET_LENGTH=${CLOUDLET_LENGTH:-1000}

read -p "Enter CLOUDLET_PES (default: 4): " CLOUDLET_PES
CLOUDLET_PES=${CLOUDLET_PES:-4}

read -p "Enter FILE_SIZE (default: 15212544): " FILE_SIZE
FILE_SIZE=${CLOUDLET_LENGTH:-15212544}

read -p "Enter OUTPUT_SIZE (default: 15212544): " OUTPUT_SIZE
OUTPUT_SIZE=${OUTPUT_SIZE:-15212544}

read -p "Enter POWER_SPEC_FILENAME (N to manual set) (default: N)" POWER_SPEC_FILENAME
POWER_SPEC_FILENAME=${POWER_SPEC_FILENAME:-N}
POWER_SPEC_PATH="src/main/resources/power_spec/${POWER_SPEC_FILENAME}.txt"

if [[ "$POWER_SPEC_FILENAME" == "N" ]]; then
    read -p "Enter STATIC_POWER (default: 75.0): " STATIC_POWER
    STATIC_POWER=${STATIC_POWER:-75.0}

    read -p "Enter MAX_POWER (default: 850.0): " MAX_POWER
    MAX_POWER=${MAX_POWER:-850.0}
fi

# Start writing JSON file
echo '{
    "DATACENTERS": [' > "$CONFIG_FILE"

for ((i=1; i<=NUM_DATACENTERS; i++))
do
    echo -e "\n🔹 Configuring Datacenter $i"
    if [[ "$CONFIG_DC" == "Y" ]]; then
        read -p "Enter Cloudlets per host for Node$i (default: 29): " CLOUDLET_PER_HOST
        CLOUDLET_PER_HOST=${CLOUDLET_PER_HOST:-29}

        read -p "Enter lastCloudlets per host for Node$i (default: 290): " lastCloudlets
        lastCloudlets=${lastCloudlets:-290}
    else
        CLOUDLET_PER_HOST=2
    fi

    # Compute values
    HOST_RAM=$((HOST_PES * RAM_PER_PE))
    VM_RAM=$((VM_PES * RAM_PER_PE))

    if [[ "$POWER_SPEC_FILENAME" == "N" ]]; then
        # Write to JSON file
        echo '{
            "name": "Node'"$i"'",
            "SCHEDULING_INTERVAL": '$SCHEDULING_INTERVAL',
            "hosts": '$HOSTS',
            "host_spec": {
                "HOST_PES": '$HOST_PES',
                "HOST_MIPS": '$HOST_MIPS',
                "HOST_RAM": '$HOST_RAM',
                "HOST_BW": '$HOST_BW',
                "HOST_STORAGE": '$HOST_STORAGE',
                "HOST_START_UP_DELAY": '$HOST_START_UP_DELAY',
                "HOST_SHUT_DOWN_DELAY": '$HOST_SHUT_DOWN_DELAY',
                "HOST_START_UP_POWER": '$HOST_START_UP_POWER',
                "HOST_SHUT_DOWN_POWER": '$HOST_SHUT_DOWN_POWER'
            },
            "VmAllocationPolicy": "'"$VmAllocationPolicy"'",
            "VmScheduler": "'"$VmScheduler"'",
            "UtilizationModel": "'"$UtilizationModel"'",
            "vm": '$VM_PER_HOST',
            "cloudlets": '$CLOUDLET_PER_HOST',
            "lastCloudlets": '$lastCloudlets',
            "cloudlet_spec": {
                "CLOUDLET_LENGTH": '$CLOUDLET_LENGTH',
                "CLOUDLET_PES": '$CLOUDLET_PES',
                "FILE_SIZE": '$FILE_SIZE',
                "OUTPUT_SIZE": '$OUTPUT_SIZE'
            },
            "power_spec": {
                "STATIC_POWER": '$STATIC_POWER',
                "MAX_POWER": '$MAX_POWER'
            }
        }' >> "$CONFIG_FILE"
    else
        # Write to JSON file
        echo '{
            "name": "Node'"$i"'",
            "SCHEDULING_INTERVAL": '$SCHEDULING_INTERVAL',
            "hosts": '$HOSTS',
            "host_spec": {
                "HOST_PES": '$HOST_PES',
                "HOST_MIPS": '$HOST_MIPS',
                "HOST_RAM": '$HOST_RAM',
                "HOST_BW": '$HOST_BW',
                "HOST_STORAGE": '$HOST_STORAGE',
                "HOST_START_UP_DELAY": '$HOST_START_UP_DELAY',
                "HOST_SHUT_DOWN_DELAY": '$HOST_SHUT_DOWN_DELAY',
                "HOST_START_UP_POWER": '$HOST_START_UP_POWER',
                "HOST_SHUT_DOWN_POWER": '$HOST_SHUT_DOWN_POWER'
            },
            "VmAllocationPolicy": "'"$VmAllocationPolicy"'",
            "VmScheduler": "'"$VmScheduler"'",
            "UtilizationModel": "'"$UtilizationModel"'",
            "vm": '$VM_PER_HOST',
            "cloudlets": '$CLOUDLET_PER_HOST',
            "lastCloudlets": '$lastCloudlets',
            "cloudlet_spec": {
                "CLOUDLET_LENGTH": '$CLOUDLET_LENGTH',
                "CLOUDLET_PES": '$CLOUDLET_PES',
                "FILE_SIZE": '$FILE_SIZE',
                "OUTPUT_SIZE": '$OUTPUT_SIZE'
            },
            "power_spec_path": "'$POWER_SPEC_PATH'"
        }' >> "$CONFIG_FILE"
    fi


    # Add comma except for last entry
    if [[ $i -lt $NUM_DATACENTERS ]]; then
        echo ',' >> "$CONFIG_FILE"
    fi
done

echo ']}' >> "$CONFIG_FILE"