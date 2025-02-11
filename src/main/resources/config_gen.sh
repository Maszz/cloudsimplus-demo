#!/bin/bash

# Ask for the configuration file name
read -p "Enter the name of the config file (default: config.json): " CONFIG_FILE
CONFIG_FILE=${CONFIG_FILE:-config.json}  # Use default if empty

# Ask for the number of datacenters
read -p "Enter number of datacenters: " NUM_DATACENTERS

# Ask if the user wants to configure Datacenter settings individually
read -p "Do you want to configure each Datacenter manually? (Y/N): " CONFIG_DC
CONFIG_DC=$(echo "${CONFIG_DC:-N}" | tr '[:lower:]' '[:upper:]')

# Start writing JSON file
echo '{
    "SCHEDULING_INTERVAL": 1.0,
    "DATACENTERS": [' > "$CONFIG_FILE"

for ((i=1; i<=NUM_DATACENTERS; i++))
do
    echo -e "\n🔹 Configuring Datacenter $i"

    if [[ "$CONFIG_DC" == "Y" ]]; then
        # Prompt user for each datacenter setting
        read -p "Enter number of hosts for Node$i (default: 250): " HOSTS
        HOSTS=${HOSTS:-250}

        read -p "Enter VMs per host for Node$i (default: 4): " VM_PER_HOST
        VM_PER_HOST=${VM_PER_HOST:-4}

        read -p "Enter Cloudlets per host for Node$i (default: 2): " CLOUDLET_PER_HOST
        CLOUDLET_PER_HOST=${CLOUDLET_PER_HOST:-2}

        read -p "Enter HOST_PES (default: 16): " HOST_PES
        HOST_PES=${HOST_PES:-16}

        read -p "Enter VM_PES (default: 4): " VM_PES
        VM_PES=${VM_PES:-4}

        read -p "Enter RAM per Processing Element (RAM_PER_PE) in MB (default: 2048): " RAM_PER_PE
        RAM_PER_PE=${RAM_PER_PE:-2048}
    else
        # Use default values if CONFIG_DC is "N"
        HOSTS=250
        VM_PER_HOST=4
        CLOUDLET_PER_HOST=2
        HOST_PES=16
        VM_PES=4
        RAM_PER_PE=2048
    fi

    # Compute values
    VM_COUNT=$((HOSTS * VM_PER_HOST))
    CLOUDLET_COUNT=$((HOSTS * CLOUDLET_PER_HOST))
    VM_RAM=$((VM_PES * RAM_PER_PE))
    CLOUDLET_LENGTH=$((7500000 + (i * 100000)))

    # Write to JSON file
    echo '{
        "name": "Node'"$i"'",
        "hosts": '"$HOSTS"',
        "host_spec": {
            "HOST_PES": '"$HOST_PES"',
            "HOST_MIPS": 38400,
            "HOST_RAM": 32768,
            "HOST_BW": 1024,
            "HOST_STORAGE": 1000000
        },
        "vm": '"$VM_COUNT"',
        "vm_spec": {
            "VM_PES": '"$VM_PES"',
            "VM_RAM": '"$VM_RAM"',
            "VM_BW": 300000,
            "VM_STORAGE": 256
        },
        "cloudlets": '"$CLOUDLET_COUNT"',
        "cloudlet_spec": {
            "CLOUDLET_LENGTH": '"$CLOUDLET_LENGTH"',
            "CLOUDLET_PES": 4
        },
        "power_spec": {
            "HOST_START_UP_DELAY": 0.0,
            "HOST_SHUT_DOWN_DELAY": 0.0,
            "HOST_START_UP_POWER": 5.0,
            "HOST_SHUT_DOWN_POWER": 3.0,
            "STATIC_POWER": 80.0,
            "MAX_POWER": 800.0
        }
    }' >> "$CONFIG_FILE"

    # Add comma except for last entry
    if [[ $i -lt $NUM_DATACENTERS ]]; then
        echo ',' >> "$CONFIG_FILE"
    fi
done

echo ']}' >> "$CONFIG_FILE"

