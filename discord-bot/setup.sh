#!/bin/bash

# Grand Flip Out Discord Bot Setup Script

echo "================================"
echo "Grand Flip Out Discord Bot Setup"
echo "================================"
echo ""

# Check Python
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 not found. Please install Python 3.8+"
    exit 1
fi

echo "✓ Python 3 found: $(python3 --version)"
echo ""

# Install dependencies
echo "Installing dependencies..."
pip install -r requirements.txt

if [ $? -eq 0 ]; then
    echo "✓ Dependencies installed successfully"
else
    echo "❌ Failed to install dependencies"
    exit 1
fi

echo ""

# Check if .env exists
if [ ! -f .env ]; then
    echo "Creating .env file from template..."
    cp .env.example .env
    echo "✓ Created .env file"
    echo ""
    echo "⚠️  Please edit .env and add your Discord token:"
    echo "   - Get it from: https://discord.com/developers/applications"
    echo "   - Open .env and paste your token next to DISCORD_TOKEN"
    echo ""
else
    echo "✓ .env file already exists"
fi

echo ""
echo "Setup complete! To start the bot:"
echo "  python bot.py"
echo ""
echo "Make sure:"
echo "  1. Your Discord token is in .env"
echo "  2. Backend API is running at http://localhost:3001"
echo "  3. Bot is invited to your Discord server"
echo ""
