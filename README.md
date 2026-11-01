# Minecraft Found Footage

![Minecraft Found Footage](MinecraftFoundFootage.png)

A **horror mod** for Minecraft with **integrated AI** that learns from players and adapts to their behavior in real-time.

---

## Features

### Skin Walker with AI

The mod's most terrifying entity features:

- **Movement Learning** - Analyzes and replicates each player's movement patterns
- **Behavioral Prediction** - Predicts where players will go and ambushes them
- **Synthetic Voice** - Generates robotic voice that mimics captured players
- **Multi-language** - Responds in Spanish, English, German, French, Italian, and Portuguese
- **Intelligent Chat** - Answers questions and holds conversations via voice and text

### Advanced Voice System

- **Voice Modulator** - All players sound the same, nobody can tell who is who
- **Robotic Voice Synthesis** - Generates audio from text using formant synthesis
- **Language Detection** - Automatically responds in the player's language
- **Proximity Voice Chat** - Full interaction through microphone

### Adaptive Learning

- **Movement Pattern Analysis** - Speed, direction, exploration habits
- **Behavior Prediction** - Uses history to anticipate actions
- **Playstyle Detection** - Identifies if player is cautious, aggressive, or social
- **Persistent Memory** - Remembers patterns across sessions

### Multi-language Support

| Language | Commands | Chat | Voice |
|----------|----------|------|-------|
| English | ✅ | ✅ | ✅ |
| Spanish | ✅ | ✅ | ✅ |
| German | ✅ | ✅ | ✅ |
| French | ✅ | ✅ | ✅ |
| Italian | ✅ | ✅ | ✅ |
| Portuguese | ✅ | ✅ | ✅ |
| Russian | ✅ | ✅ | ✅ |
| Chinese | ✅ | ✅ | ✅ |
| Japanese | ✅ | ✅ | ✅ |
| Korean | ✅ | ✅ | ✅ |

---

## Commands

| Command | Description | Permissions |
|---------|-------------|-------------|
| `/skinwalker <player>` | Activates Skin Walker on a player | OP |
| `/release <player>` | Releases the captured player | OP |
| `/skinwalkerlang <language>` | Changes the Skin Walker's language | Everyone |

### Available languages for `/skinwalkerlang`:
`en` (English) and `es` (Spanish), the languages the mod is fully ready in, or `auto` to answer in the language you speak.

The first time a player joins, the mod picks their game language (English if it is not one of the above) and tells them, in chat, how to change it.

---

## AI Libraries

The mod includes 3 custom AI libraries:

| Library | Function |
|---------|----------|
| **JavaAI** | Learns player movement inputs and patterns |
| **VoiceJavaAI** | Synthesizes robotic voice from text |
| **JavaChatAI** | Conversational agent with intent detection |

---

## How It Works

### 1. Capture
The Skin Walker selects the least active player in voice chat and captures them when no one is watching.

### 2. Mimicry
The entity replicates the captured player's appearance and voice, confusing others.

### 3. Learning
While interacting, it analyzes:
- Movement patterns
- Playstyle
- Reactions to fear
- Exploration habits

### 4. Hunt
In true form, it uses learned knowledge to:
- Predict player movement
- Ambush at strategic points
- Adapt speed and tactics

---

## Metrics It Learns

| Metric | Description |
|--------|-------------|
| `aggression` | Player's aggression level |
| `caution` | Tendency to be cautious |
| `predictability` | How predictable their movement is |
| `sprint_tendency` | Sprint frequency |
| `sneak_tendency` | Sneak frequency |
| `jump_frequency` | Jump frequency |
| `avg_speed` | Average speed |
| `movement_variance` | Movement variability |

---

## Requirements

- Minecraft 1.20.1
- Fabric Loader 0.16.5+
- Fabric API
- Simple Voice Chat (recommended)

---

## Installation

1. Download the JAR from [Releases](https://github.com/SpacePotatoee/SPBackrooms-Revamped/releases)
2. Place the JAR in the `mods/` folder
3. Install [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) (optional but recommended)
4. Start the server and enjoy the horror

---

## Tips

- Use `/skinwalkerlang en` to configure the language
- The Skin Walker responds faster to simple questions
- In true form it is invulnerable
- Voice chat is key to the full experience

---

## License

MIT License

---

## Credits

- **Original Development** - [SpacePotatoee](https://github.com/SpacePotatoee)
- **AI Update & Integration** - [Chagui68](https://github.com/Chagui68)
- **AI Libraries** - JavaAI, VoiceJavaAI, JavaChatAI
- **Voice API** - Simple Voice Chat API
- **Animations** - GeckoLib
- **Components** - Cardinal Components API

---

> *"You're not alone in the Backrooms... something is watching you."*
