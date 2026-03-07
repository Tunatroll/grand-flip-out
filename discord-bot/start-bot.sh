#!/bin/bash
echo "================================"
echo " Grand Flip Out Discord Bot"
echo "================================"
echo ""

# Install deps if needed
pip3 install -r requirements.txt --quiet 2>/dev/null || pip install -r requirements.txt --quiet

# Check .env
if [ ! -f .env ]; then
    echo "ERROR: No .env file found! Create one with your DISCORD_TOKEN"
    exit 1
fi

echo "Starting Grand Flip Out bot..."
echo "Press Ctrl+C to stop"
echo ""
python3 bot.py || python bot.py
