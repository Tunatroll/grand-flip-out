# Command Examples and Expected Output

## /price ranarr seed

**Command:** `/price ranarr seed`

**Expected Output:**
```
┌─────────────────────────────────────────────┐
│ Ranarr Seed                                 │
├─────────────────────────────────────────────┤
│ Current Price: 50,123 gp                    │
│ Buy Price: 49,000 gp   Sell Price: 51,200 gp │
│ Margin: 2,200 gp                           │
│ JTI Score: 42.15                           │
│ Buy Volume: 1,234      Sell Volume: 987    │
│ [OSRS Wiki Item Icon]                      │
│ Grand Flip Out • OSRS Flipping Intelligence │
└─────────────────────────────────────────────┘
```

**Features:**
- Fuzzy matches "ranarr seed" even if you type "ranarr"
- Shows all pricing metrics
- Displays OSRS Wiki icon
- Gold color theme (#FF981F)

---

## /top jti 10

**Command:** `/top jti 10`

**Expected Output:**
```
┌─────────────────────────────────────────────┐
│ Top 10 Items by JTI                         │
│ The hottest flips right now!                │
├─────────────────────────────────────────────┤
│ 1. Ranarr Seed                              │
│    JTI: 45.32                              │
│                                             │
│ 2. Irit Leaf                               │
│    JTI: 43.87                              │
│                                             │
│ 3. Avantoe                                 │
│    JTI: 42.15                              │
│                                             │
│ [... 7 more items ...]                     │
│ Grand Flip Out • OSRS Flipping Intelligence │
└─────────────────────────────────────────────┘
```

**Features:**
- Supports sorting: jti, margin, volume
- Configurable limit (1-20)
- Numbered list format
- Different values shown per sort type

---

## /dumps

**Command:** `/dumps`

**Expected Output (when dumps exist):**
```
┌─────────────────────────────────────────────┐
│ Dump Alerts - Items Crashing!               │
│ Items dropping >5% in 5 minutes              │
├─────────────────────────────────────────────┤
│ 📉 Limpwurt Root                           │
│    Down 12.3% → 234,567 gp                │
│                                             │
│ 📉 Super Energy Potion                     │
│    Down 8.7% → 1,234,567 gp               │
│                                             │
│ [... more dumps ...]                       │
│ Grand Flip Out • OSRS Flipping Intelligence │
└─────────────────────────────────────────────┘
```

**Expected Output (no dumps):**
```
No dumps detected. Market is stable.
```

**Features:**
- Red color theme (danger)
- Shows percentage drop
- Lists current prices
- Limited to 10 items

---

## /pumps

**Command:** `/pumps`

**Expected Output:**
```
┌─────────────────────────────────────────────┐
│ Pump Alerts - Unusual Buy Pressure!         │
├─────────────────────────────────────────────┤
│ 📈 Ranarr Seed                              │
│    Buy: 5,432 | Sell: 1,234                │
│                                             │
│ 📈 Irit Leaf                               │
│    Buy: 3,210 | Sell: 890                  │
│                                             │
│ [... more pumps ...]                       │
│ Grand Flip Out • OSRS Flipping Intelligence │
└─────────────────────────────────────────────┘
```

**Features:**
- Green color theme (positive)
- Shows buy vs sell volume
- Items with high buy pressure
- Up to 10 items

---

## /recipe

**Command:** `/recipe`

**Expected Output:**
```
┌─────────────────────────────────────────────┐
│ Profitable Processing Recipes               │
│ Herb cleaning, gem cutting, and more        │
├─────────────────────────────────────────────┤
│ 1. Guam Leaf → Guam Herb                   │
│    Profit: 45 gp                           │
│                                             │
│ 2. Marrentill → Clean Marrentill           │
│    Profit: 78 gp                           │
│                                             │
│ 3. Uncut Ruby → Ruby                       │
│    Profit: 1,234 gp                        │
│                                             │
│ [... more recipes ...]                     │
│ Grand Flip Out • OSRS Flipping Intelligence │
└─────────────────────────────────────────────┘
```

**Features:**
- Shows input → output items
- Profit per item
- Up to 10 recipes
- Process-based flipping

---

## /jti ranarr seed

**Command:** `/jti ranarr seed`

**Expected Output:**
```
┌─────────────────────────────────────────────┐
│ JTI Breakdown - Ranarr Seed                │
├─────────────────────────────────────────────┤
│ JTI Score: 42.15                           │
│ Profit Margin: 2,200 gp                    │
│ Buy Volume: 1,234        Sell Volume: 987  │
│                                             │
│ Recent Price History                        │
│ 2025-03-05 11:00: 50,100 gp               │
│ 2025-03-05 10:50: 50,050 gp               │
│ 2025-03-05 10:40: 49,900 gp               │
│ 2025-03-05 10:30: 49,850 gp               │
│ 2025-03-05 10:20: 50,200 gp               │
│                                             │
│ JTI = Jump The Queue Index • Higher is better │
└─────────────────────────────────────────────┘
```

**Features:**
- Detailed breakdown of JTI components
- Recent price history (5 data points)
- Item icon
- Explanation of JTI

---

## /market

**Command:** `/market`

**Expected Output:**
```
┌─────────────────────────────────────────────┐
│ OSRS Market Summary                         │
│ Overall flipping market statistics           │
├─────────────────────────────────────────────┤
│ Total Daily Volume: 5,234,567 items        │
│ Average Margin: 1,234 gp                   │
│ Active Items: 487 items                    │
│                                             │
│ Highest Margin Item                         │
│ Uncut Diamond: 12,345 gp                   │
│                                             │
│ Grand Flip Out • OSRS Flipping Intelligence │
└─────────────────────────────────────────────┘
```

**Features:**
- Aggregated market statistics
- Average profit margins
- Count of active items
- Top performing item

---

## /compare ranarr seed irit leaf

**Command:** `/compare ranarr seed irit leaf`

**Expected Output:**
```
┌──────────────────────────────────────────────────┐
│ Comparison: Ranarr Seed vs Irit Leaf           │
├──────────────────────────────────────────────────┤
│ Buy Price                                        │
│ Ranarr Seed: 49,000 gp                         │
│ Irit Leaf: 51,200 gp                           │
│                                                  │
│ Sell Price                                       │
│ Ranarr Seed: 51,200 gp                         │
│ Irit Leaf: 53,400 gp                           │
│                                                  │
│ Profit Margin                                    │
│ Ranarr Seed: 2,200 gp                          │
│ Irit Leaf: 2,200 gp                            │
│                                                  │
│ JTI Score                                        │
│ Ranarr Seed: 42.15        Irit Leaf: 40.87    │
│                                                  │
│ Buy Volume                                       │
│ Ranarr Seed: 1,234        Irit Leaf: 987      │
│                                                  │
│ Sell Volume                                      │
│ Ranarr Seed: 987          Irit Leaf: 1,123    │
│ Grand Flip Out • OSRS Flipping Intelligence    │
└──────────────────────────────────────────────────┘
```

**Features:**
- Side-by-side comparison
- All key metrics
- Easy differentiation
- Fuzzy matching for both items

---

## /watchlist show

**Command:** `/watchlist show`

**Expected Output (with items):**
```
┌─────────────────────────────────────────────┐
│ GoldFlipper's Watchlist                     │
│ Your watched flipping opportunities           │
├─────────────────────────────────────────────┤
│ Ranarr Seed: 2,200 gp                       │
│ Irit Leaf: 2,200 gp                         │
│ Avantoe: 1,876 gp                           │
│ Limpwurt Root: 1,456 gp                     │
│ Super Energy (unf): 890 gp                  │
│ Grand Flip Out • OSRS Flipping Intelligence │
└─────────────────────────────────────────────┘
```

**Expected Output (empty):**
```
Your watchlist is empty. Use `/watchlist add <item_name>` to add items!
```

---

## /watchlist add ranarr seed

**Command:** `/watchlist add ranarr seed`

**Expected Output:**
```
Added Ranarr Seed to your watchlist! ✓
```

**Features:**
- Fuzzy item matching
- Prevents duplicates
- Confirmation message

---

## /watchlist remove ranarr seed

**Command:** `/watchlist remove ranarr seed`

**Expected Output:**
```
Removed Ranarr Seed from your watchlist. ✓
```

**Features:**
- Fuzzy matching
- Works only if item in watchlist
- Confirmation message

---

## /sinks

**Command:** `/sinks`

**Expected Output:**
```
┌─────────────────────────────────────────────┐
│ GP Sinks - Gold Leaving Economy             │
│ Items with high sell volume (players removing GP) │
├─────────────────────────────────────────────┤
│ 💸 Coins                                    │
│    Buy: 234 | Sell: 5,432                  │
│                                             │
│ 💸 Gold Ore                                 │
│    Buy: 456 | Sell: 3,210                  │
│                                             │
│ [... more items ...]                       │
│ Grand Flip Out • OSRS Flipping Intelligence │
└─────────────────────────────────────────────┘
```

**Features:**
- Red color theme (GP leaving)
- High sell volume relative to buy
- Economic impact visualization
- Up to 10 items

---

## /setup

**Command:** `/setup`

**Expected Output:**
```
┌──────────────────────────────────────────────────┐
│ Grand Flip Out - Setup Info                     │
│ OSRS Grand Exchange Flipping Intelligence        │
├──────────────────────────────────────────────────┤
│ API Status: ✓ Online                            │
│ API URL: http://localhost:3001                  │
│ Bot Version: 1.0.0                              │
│                                                  │
│ Available Commands                               │
│ /price - Item price lookup                      │
│ /top - Top items by metric                      │
│ /dumps - Dump alerts                            │
│ [... all 11 commands ...]                       │
│                                                  │
│ Tips                                             │
│ - Use fuzzy item name matching                  │
│ - Check /dumps and /pumps regularly             │
│ - Build a watchlist with /watchlist add         │
│ [... more tips ...]                             │
│ Buying gf 10k • Grand Flip Out                  │
└──────────────────────────────────────────────────┘
```

**Features:**
- API health check
- Command summary
- Usage tips
- OSRS easter egg

---

## Automatic Dump Alert Example

**Channel:** #dump-alerts

**Example Alert (posted every minute if severe dump detected):**
```
┌─────────────────────────────────────────────┐
│ SEVERE DUMP ALERT!                          │
│ Ranarr Seed is crashing!                    │
├─────────────────────────────────────────────┤
│ Price Drop: 15.2%                           │
│ Current Price: 41,200 gp                    │
│ Grand Flip Out Alert System                 │
│ 2025-03-05 11:23:45                        │
└─────────────────────────────────────────────┘
```

---

## Error Messages

### Item Not Found
```
Couldn't find item matching 'xyz'. Try a different name.
```

### API Error
```
Error fetching item data: API request timed out
```

### Missing Parameter
```
Please specify an item name to add!
```

### No Results
```
No dumps detected. Market is stable.
```

---

## Color Codes Used

| Color | Hex | Use Case |
|-------|-----|----------|
| Gold | #FF981F | Main theme, prices, markets |
| Green | #00FF00 | Pumps, positive signals |
| Red | #FF0000 | Dumps, negative signals, sinks |
| Blue | #0099FF | Market data |

---

## GP Formatting Examples

| Value | Formatted |
|-------|-----------|
| 1000 | 1,000 gp |
| 50000 | 50,000 gp |
| 1234567 | 1,234,567 gp |
| 0 | N/A |
| null | N/A |

---

## Timestamps

All embeds show current timestamp in format:
```
2025-03-05 11:23:45 UTC
```

Helps users understand data freshness.
