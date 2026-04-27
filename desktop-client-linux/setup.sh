#!/bin/bash
# Gorilla — Linux setup
sudo apt-get update
sudo apt-get install -y python3-tk python3-pip xdotool
pip3 install websockets --break-system-packages
echo ""
echo "Setup complete. Run with:"
echo "  python3 client.py"
