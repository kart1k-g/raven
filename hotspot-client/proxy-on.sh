#!/bin/bash

# --- CONFIGURATION ---
# Replace with your phone hotspot's Wi-Fi Name (SSID)
HOTSPOT_SSID="iQOO Neo 10R"
PROXY_PORT="1080"
# ---------------------

echo "Detecting network connection..."

# Get the name (SSID) of the currently connected Wi-Fi network
CURRENT_SSID=$(nmcli -t -f active,ssid dev wifi | grep '^yes' | cut -d':' -f2)

if [ "$CURRENT_SSID" != "$HOTSPOT_SSID" ]; then
    echo "Error: Not connected to hotspot '$HOTSPOT_SSID'."
    if [ -n "$CURRENT_SSID" ]; then
        echo "Currently connected to: $CURRENT_SSID"
    else
        echo "Not connected to any Wi-Fi."
    fi
    exit 1
fi

echo "Connected to correct hotspot: $HOTSPOT_SSID"

# Because the phone is hosting the network, it is the default gateway.
PROXY_IP=$(ip route show default | awk '/default/ {print $3}')

if [ -z "$PROXY_IP" ]; then
    echo "Error: Could not determine the default gateway IP."
    exit 1
fi

echo "Phone detected at IP (Gateway): $PROXY_IP"

# 1. Create a virtual TUN interface named 'tun0'
sudo ip tuntap add mode tun dev tun0 2>/dev/null
sudo ip addr add 198.18.0.1/15 dev tun0
sudo ip link set dev tun0 up

# 2. Start tun2socks in the background and save its process ID (PID)
echo "Starting tun2socks..."
sudo tun2socks -device tun://tun0 -proxy socks5://$PROXY_IP:$PROXY_PORT > /tmp/tun2socks.log 2>&1 &
echo $! | sudo tee /tmp/tun2socks.pid > /dev/null

# 3. Modify routing table to push all traffic through tun0
echo "Updating routing tables..."
# Route traffic destined for the proxy itself through the normal gateway (prevents infinite loop)
sudo ip route add $PROXY_IP via $PROXY_IP dev $(ip route show default | awk '/default/ {print $5}')
# Route everything else through the TUN interface
sudo ip route add default dev tun0 metric 1

# 4. Update DNS to use DNS-over-TLS (TCP) so it routes through Raven
echo "Configuring DNS-over-TLS..."
sudo mkdir -p /etc/systemd/resolved.conf.d
echo -e "[Resolve]\nDNS=1.1.1.1\nDNSOverTLS=yes" | sudo tee /etc/systemd/resolved.conf.d/proxy-dns.conf > /dev/null
sudo systemctl restart systemd-resolved


echo "System-wide SOCKS5 proxy is now ACTIVE!"
