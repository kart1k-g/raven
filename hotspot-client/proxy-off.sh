#!/bin/bash

echo "Stopping proxy and restoring network settings..."

# 1. Restore the original DNS settings
if [ -f /etc/systemd/resolved.conf.d/proxy-dns.conf ]; then
    sudo rm /etc/systemd/resolved.conf.d/proxy-dns.conf
    sudo systemctl restart systemd-resolved
fi

# 2. Kill the tun2socks background process
if [ -f /tmp/tun2socks.pid ]; then
    sudo kill $(cat /tmp/tun2socks.pid) 2>/dev/null
    sudo rm /tmp/tun2socks.pid
else
    # Fallback kill if PID file is missing
    sudo pkill -f tun2socks
fi

# 3. Remove the specific route to the proxy IP
# We have to guess the IP if it's not saved, so we just flush routes related to tun0
sudo ip route del default dev tun0 metric 1 2>/dev/null
sudo ip -6 route del default dev tun0 metric 1 2>/dev/null

# 4. Delete the virtual TUN interface (this also drops the specific IP route attached to it)
sudo ip link set dev tun0 down 2>/dev/null
sudo ip tuntap del mode tun dev tun0 2>/dev/null

# Restart NetworkManager to ensure everything goes back to normal cleanly
sudo systemctl restart NetworkManager

echo "System-wide proxy disabled. Normal network restored."
