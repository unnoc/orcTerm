#!/bin/bash
set -e

# OrcTerm "Scan to Connect" Setup Script
# Role: Server-side provisioning & QR Code generation
# Security: AES-256-CBC Encryption for QR Payload

# Function: Install Dependencies
install_deps() {
    local pkgs="openssl qrencode jq python3"
    echo "Missing dependencies. Attempting to install: $pkgs"
    
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$ID
    else
        OS=$(uname -s)
    fi

    if command -v apt-get &> /dev/null; then
        echo "Detected OS: Debian/Ubuntu ($OS)"
        sudo apt-get update -q
        sudo apt-get install -y $pkgs
    elif command -v dnf &> /dev/null; then
        echo "Detected OS: Fedora/RHEL ($OS)"
        sudo dnf install -y $pkgs
    elif command -v yum &> /dev/null; then
        echo "Detected OS: CentOS/RHEL ($OS)"
        # Try to install EPEL if likely needed (often for jq/qrencode on older RHEL)
        sudo yum install -y epel-release || true
        sudo yum install -y $pkgs
    elif command -v pacman &> /dev/null; then
        echo "Detected OS: Arch Linux ($OS)"
        sudo pacman -Sy --noconfirm $pkgs
    elif command -v apk &> /dev/null; then
        echo "Detected OS: Alpine Linux ($OS)"
        sudo apk add $pkgs
    elif command -v zypper &> /dev/null; then
        echo "Detected OS: OpenSUSE ($OS)"
        sudo zypper install -y $pkgs
    else
        echo "Error: Could not detect a supported package manager."
        echo "Please manually install: $pkgs"
        exit 1
    fi
}

# 1. Prerequisite Check & Auto-Install
if ! command -v openssl &> /dev/null || ! command -v qrencode &> /dev/null || ! command -v jq &> /dev/null || ! command -v python3 &> /dev/null; then
    install_deps
fi

# Verify again
for cmd in openssl qrencode jq python3; do
    if ! command -v $cmd &> /dev/null; then
        echo "Error: Failed to install '$cmd'. Please install it manually."
        exit 1
    fi
done

echo "=== OrcTerm Server Setup ==="

# 2. Information Gathering
read -p "Enter username to create/use (default: orc_user): " ORC_USER
ORC_USER=${ORC_USER:-orc_user}

# IP Detection
echo "Detecting IPs..."
LAN_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
WAN_IP=$(curl -s --max-time 3 ifconfig.me || echo "")

echo "Available IPs:"
echo "1) LAN: ${LAN_IP:-Unknown}"
echo "2) WAN: ${WAN_IP:-Unknown}"
echo "3) Manual Entry"

read -p "Select IP [1-3] (default: 1): " IP_CHOICE
IP_CHOICE=${IP_CHOICE:-1}

case $IP_CHOICE in
    1) SERVER_HOST=$LAN_IP ;;
    2) SERVER_HOST=$WAN_IP ;;
    *) read -p "Enter Hostname/IP: " SERVER_HOST ;;
esac

if [ -z "$SERVER_HOST" ]; then echo "Host is required."; exit 1; fi

read -p "Enter SSH Port (default: 22): " SERVER_PORT
SERVER_PORT=${SERVER_PORT:-22}

# 3. User & Key Setup
if id "$ORC_USER" &>/dev/null; then
    echo "User $ORC_USER exists. Using existing user."
else
    echo "Creating user $ORC_USER..."
    sudo useradd -m -s /bin/bash "$ORC_USER"
fi

# Ensure .ssh directory exists
SSH_DIR="/home/$ORC_USER/.ssh"
if [ ! -d "$SSH_DIR" ]; then
    sudo -u "$ORC_USER" mkdir -p "$SSH_DIR"
    sudo -u "$ORC_USER" chmod 700 "$SSH_DIR"
fi

# 4. Mode Selection
echo "Select Setup Mode:"
echo "1) Secure Binding (Recommended) - Generates key on phone, zero network exposure"
echo "2) Legacy Import - Generates key on server, transfers via QR (Less secure)"
read -p "Select Mode [1-2] (default: 1): " MODE_CHOICE
MODE_CHOICE=${MODE_CHOICE:-1}

if [ "$MODE_CHOICE" == "2" ]; then
    # --- LEGACY IMPORT MODE ---
    
    # Generate Key if missing
    KEY_FILE="$SSH_DIR/id_ed25519"
    if [ ! -f "$KEY_FILE" ]; then
        echo "Generating SSH KeyPair (Ed25519)..."
        sudo -u "$ORC_USER" ssh-keygen -t ed25519 -f "$KEY_FILE" -N "" -C "orcterm_mobile_access"
        
        # Add to authorized_keys
        sudo -u "$ORC_USER" sh -c "cat $KEY_FILE.pub >> $SSH_DIR/authorized_keys"
        sudo -u "$ORC_USER" chmod 600 "$SSH_DIR/authorized_keys"
    fi

    # Read Private Key
    PRIV_KEY=$(sudo cat "$KEY_FILE")

    # Payload Construction
    JSON_PAYLOAD=$(jq -n -c \
                      --arg h "$SERVER_HOST" \
                      --arg p "$SERVER_PORT" \
                      --arg u "$ORC_USER" \
                      --arg k "$PRIV_KEY" \
                      '{h: $h, p: $p, u: $u, k: $k}')

    # Encryption
    ENC_PASS=$(openssl rand -hex 3)

    echo "------------------------------------------------"
    echo "Encryption Password: $ENC_PASS"
    echo "(You MUST enter this password in the App)"
    echo "------------------------------------------------"

    ENC_DATA=$(echo -n "$JSON_PAYLOAD" | openssl enc -aes-256-cbc -pbkdf2 -iter 10000 -pass "pass:$ENC_PASS" -base64 -A)
    
    # URL Encode the Base64 data to ensure it's safe for URI query parameters
    # Using python3 for reliable URL encoding
    ENC_DATA_ENCODED=$(echo -n "$ENC_DATA" | python3 -c "import urllib.parse, sys; print(urllib.parse.quote(sys.stdin.read()))")
    
    URI="orcterm://import?v=1&data=$ENC_DATA_ENCODED"

    echo "Scan this QR code with OrcTerm App:"
    qrencode -t ANSIUTF8 "$URI"

    echo "------------------------------------------------"
    echo "Done. User '$ORC_USER' is ready."

else
    # --- SECURE BINDING MODE ---
    
    start_bind_mode() {
        # 1. Prepare Environment
        API_PORT=$(shuf -i 30000-60000 -n 1)
        TOKEN=$(openssl rand -hex 16)
        
        # 2. Generate Temporary SSH Key (for tunnel establishment only)
        TEMP_KEY_FILE="/tmp/orc_temp_${TOKEN}"
        rm -f "${TEMP_KEY_FILE}" "${TEMP_KEY_FILE}.pub"
        ssh-keygen -t ed25519 -f "$TEMP_KEY_FILE" -N "" -q -C "orcterm_temp_access"
        
        TEMP_PUB_KEY=$(cat "${TEMP_KEY_FILE}.pub")
        # Extract Private Key Body (remove headers/footers) for compact QR
        TEMP_PRIV_BODY=$(cat "$TEMP_KEY_FILE" | grep -v "PRIVATE KEY" | tr -d '\n')
        
        # 3. Add Temp Key to authorized_keys with strict restrictions
        # Only allow port forwarding to the local API port
        AUTH_KEYS_FILE="$SSH_DIR/authorized_keys"
        RESTRICTIONS="restrict,port-forwarding,permitopen=\"127.0.0.1:$API_PORT\",command=\"echo OrcTerm Binding...; sleep 60\""
        
        # Ensure .ssh exists and has correct permissions
        if [ ! -d "$SSH_DIR" ]; then
            sudo -u "$ORC_USER" mkdir -p "$SSH_DIR"
            sudo -u "$ORC_USER" chmod 700 "$SSH_DIR"
        fi
        
        echo "Adding temporary access key..."
        FULL_ENTRY="$RESTRICTIONS $TEMP_PUB_KEY # OrcTerm-Temp-$TOKEN"
        echo "$FULL_ENTRY" | sudo -u "$ORC_USER" tee -a "$AUTH_KEYS_FILE" > /dev/null
        sudo -u "$ORC_USER" chmod 600 "$AUTH_KEYS_FILE"
        
        # 4. Generate Compressed QR Payload
        # Format: IP:PORT:USER:API_PORT:TOKEN:TEMP_KEY_BODY
        RAW_DATA="$SERVER_HOST:$SERVER_PORT:$ORC_USER:$API_PORT:$TOKEN:$TEMP_PRIV_BODY"
        
        # Compress: Gzip -> Base64
        COMPRESSED_DATA=$(echo -n "$RAW_DATA" | gzip -c | base64 | tr -d '\n')
        
        QR_URI="orcterm://bind/$COMPRESSED_DATA"
        
        echo "------------------------------------------------"
        echo "Scan this QR code with OrcTerm App (Secure Mode)"
        echo "------------------------------------------------"
        qrencode -t ANSIUTF8 "$QR_URI"
        
        echo ""
        echo "Waiting for App to connect and bind key..."
        echo "Service will auto-close in 60 seconds."
        
        # 5. Start Local Python Server
        cat <<EOF > /tmp/orc_bind_${TOKEN}.py
import http.server, json, sys, os

TOKEN = "$TOKEN"
AUTH_KEYS = "$AUTH_KEYS_FILE"
TEMP_MARKER = "OrcTerm-Temp-$TOKEN"

class H(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != '/bind':
            self.send_error(404)
            return
            
        try:
            length = int(self.headers['Content-Length'])
            data = json.loads(self.rfile.read(length))
            
            if data.get('token') != TOKEN:
                self.send_error(403)
                return
                
            new_pub_key = data.get('pub_key')
            if not new_pub_key:
                self.send_error(400)
                return
                
            # Append new key
            with open(AUTH_KEYS, "a") as f:
                f.write("\n" + new_pub_key + " # OrcTerm-Mobile\n")
            
            # Remove temp key
            lines = []
            with open(AUTH_KEYS, "r") as f:
                lines = f.readlines()
            
            with open(AUTH_KEYS, "w") as f:
                for line in lines:
                    if TEMP_MARKER not in line:
                        f.write(line)
                        
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"OK")
            print("Bind Success!")
            sys.exit(0)
            
        except Exception as e:
            print(f"Error: {e}")
            self.send_error(500)

http.server.HTTPServer(('127.0.0.1', $API_PORT), H).serve_forever()
EOF

        # Run Python Server with Timeout
        sudo -u "$ORC_USER" timeout 60s python3 /tmp/orc_bind_${TOKEN}.py
        
        # Cleanup
        rm -f "${TEMP_KEY_FILE}" "${TEMP_KEY_FILE}.pub" "/tmp/orc_bind_${TOKEN}.py"
        
        # Fallback cleanup of authorized_keys if python didn't exit cleanly (timeout)
        sudo -u "$ORC_USER" sed -i "/OrcTerm-Temp-$TOKEN/d" "$AUTH_KEYS_FILE"
        
        echo "Binding process finished."
    }
    
    start_bind_mode
fi
