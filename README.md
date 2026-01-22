# 🌾 SkiesFarming

A feature-rich farming progression plugin for Minecraft servers that transforms farming into a rewarding skill-based system. Players gain experience from harvesting crops, level up their farming abilities, and unlock new crops as they progress.

## ✨ Features

### 🎯 **Progression System**
- **XP-based leveling** - Gain experience by harvesting mature crops
- **Customizable progression** - Choose between LINEAR, EXPONENTIAL, or CUSTOM growth modes
- **Persistent data** - Player progress automatically saved to disk
- **Visual feedback** - Action bar displays with progress bars and level-up notifications

### 🔓 **Crop Unlocking**
Players must reach specific farming levels to plant certain crops:
| Crop | Unlock Level | XP per Harvest |
|------|--------------|----------------|
| 🥕 Carrots | 0 | 4 |
| 🥔 Potatoes | 10 | 6 |
| 🌾 Wheat | 20 | 8 |
| 🌱 Beetroots | 50 | 15 |
| 🔴 Nether Wart | 100 | 24 |

### 🔄 **Auto-Refarm**
- Automatically replants crops after harvesting
- Configurable delay (default: 5 seconds)
- Optional: Only activate when not using a hoe
- Perfect for AFK farming setups

### ⏱️ **Growth Control**
- **VANILLA mode** - Standard Minecraft crop growth mechanics
- **FIXED mode** - Crops grow at a consistent, configurable rate
- Ideal for server economy balancing

### 🛡️ **Crop Protection**
- Prevents accidental breaking of immature crops
- Requires a hoe to break crops that aren't fully grown
- Helps players maximize their XP gains

### 🏆 **PlaceholderAPI Integration**
Compatible with PlaceholderAPI for scoreboards, holograms, and more:
- `%skiesfarming_level%` - Player's current farming level
- `%skiesfarming_top_1%` to `%skiesfarming_top_10%` - Leaderboard entries (format: `PlayerName:Level`)

### 🎁 **Quality of Life**
- Auto-pickup of harvested crops
- Overflow protection (items drop if inventory is full)
- Visual progress bars in action bar
- Real-time percentage display

## 📋 Requirements

- **Minecraft**: 1.15.1 or higher
- **Server**: Spigot, Paper, or compatible forks
- **Java**: Version 21 or higher
- **Optional**: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for placeholder support

## 📥 Installation

1. Download the latest release from the [Releases](../../releases) page
2. Place `skiesfarming-1.0.jar` in your server's `plugins/` folder
3. Restart your server
4. Configure the plugin (optional - see [Configuration](#-configuration))
5. Enjoy!

## ⚙️ Configuration

### config.yml

```yaml
# XP earned per mature crop harvested
crop-xp:
  CARROTS: 4
  POTATOES: 6
  WHEAT: 8
  BEETROOTS: 15
  NETHER_WART: 24

# Farming level required to plant each crop
unlock-levels:
  CARROTS: 0
  POTATOES: 10
  WHEAT: 20
  BEETROOTS: 50
  NETHER_WART: 100

# Auto-refarm settings
auto-refarm:
  enabled: true
  delay-ticks: 100  # 5 seconds (20 ticks = 1 second)
  only-when-not-using-hoe: true

# Crop growth settings
growth:
  mode: VANILLA  # Options: VANILLA or FIXED
  fixed-grow-ticks: 1200  # 60 seconds (only applies when mode is FIXED)
```

### levels.yml

Choose your preferred progression system:

#### LINEAR Growth
Fixed XP increase per level:
```yaml
growth-mode: LINEAR
linear:
  base: 100           # XP for level 1
  increment: 50       # Additional XP per level
  round-to: 5         # Round to nearest 5
```
*Example: Level 1 = 100 XP, Level 2 = 150 XP, Level 3 = 200 XP*

#### EXPONENTIAL Growth
Multiplier-based progression:
```yaml
growth-mode: EXPONENTIAL
exponential:
  base: 100           # XP for level 1
  multiplier: 1.15    # 15% increase per level
  round-to: 5         # Round to nearest 5
```
*Example: Level 1 = 100 XP, Level 2 = 115 XP, Level 3 = 132 XP*

#### CUSTOM Levels
Define each level manually:
```yaml
growth-mode: CUSTOM
levels:
  1: 100
  2: 150
  3: 200
  4: 300
  5: 500
default-per-level: 300  # Fallback for undefined levels
```

## 🎮 Usage

### For Players

1. **Start Farming**: Begin by planting and harvesting **Carrots** (available at level 0)
2. **Gain XP**: Break fully grown crops to earn experience points
3. **Track Progress**: Watch your progress bar in the action bar
4. **Level Up**: Reach new levels to unlock more valuable crops
5. **Advanced Farming**: Use the auto-refarm feature for efficient farming

**Tips:**
- Only mature crops give XP
- Use a hoe to break immature crops if needed
- Higher-level crops give more XP but require progression
- Your progress is automatically saved

### For Server Administrators

**Managing Player Data:**
- Player data is stored in `plugins/skiesfarming/players.yml`
- Format: UUID-based with XP and last known name
- Automatically saved on player quit and server shutdown

**Balancing Tips:**
- Adjust XP values to control progression speed
- Modify unlock levels to create gating
- Use FIXED growth mode for predictable farming
- Tune auto-refarm delay for server performance

**Performance Considerations:**
- Plugin uses minimal resources
- Auto-refarm uses delayed tasks, not continuous checking
- Player data is only loaded when players join

## 📊 PlaceholderAPI Examples

### Scoreboard
```yaml
- "&7Farming Level: &a%skiesfarming_level%"
```

### Leaderboard Hologram
```yaml
- "&6&l⭐ Top Farmers ⭐"
- "&e1. &f%skiesfarming_top_1%"
- "&e2. &f%skiesfarming_top_2%"
- "&e3. &f%skiesfarming_top_3%"
```

### Chat Format
```yaml
format: "&7[&aLv%skiesfarming_level%&7] &f{PLAYER}: {MESSAGE}"
```

## 🏗️ Building from Source

```bash
# Clone the repository
git clone https://github.com/SuperrSohrab/SkiesFarming/
cd skiesfarming

# Build with Maven
mvn clean package

# Find the JAR in target/skiesfarming-1.0.jar
```

**Requirements:**
- Java Development Kit (JDK) 21+
- Apache Maven 3.6+

## 🗺️ Roadmap

Future features we're considering:
- [ ] Custom farming tools with bonuses
- [ ] Team/Guild farming progression
- [ ] Farming quests and achievements


*Have a feature request? Open an [issue](../../issues)!*

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 🐛 Bug Reports

Found a bug? Please open an [issue](../../issues) with:
- Minecraft version
- Server software and version
- Plugin version
- Steps to reproduce
- Expected vs actual behavior
- Any error messages from console

## 📄 License

All rights reserved. This plugin is provided as-is for use on Minecraft servers. Redistribution or modification without permission is prohibited.

## 👥 Authors

- **ItzAcat** - *Lead Developer*
- **SuperrSohrab** - *Co-Developer*

## 🙏 Acknowledgments

- Spigot team for the excellent API
- PlaceholderAPI for integration support
- The Minecraft server community for inspiration

## 📞 Support

- **Issues**: [GitHub Issues](../../issues)
- **Discussions**: [GitHub Discussions](../../discussions)

---

<div align="center">

**Made with ❤️ for the Minecraft community**

⭐ If you like this plugin, consider giving it a star!

</div>
