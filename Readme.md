# LoginDaddy

A Fabric mod for private friend servers. Requires players to have the mod installed on their client, be on the whitelist of this mod (not the server whitelist) and authenticate with a password before they can play.

## How it works

1. Player connects → sent to an invisible limbo dimension
2. Server checks if the client has LoginDaddy installed (handshake). No mod = kicked
3. Server checks if the player is whitelisted. Not whitelisted = kicked
4. Player registers or logs in with a password
5. Player is teleported back to where they joined from

Join/leave messages and tab list are hidden until authentication is complete.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.4+
- Fabric API

## Setup

### Server
1. Drop the mod jar into your `mods/` folder
2. Start the server once to generate `logindaddy.properties`
3. Edit `logindaddy.properties` with your database credentials
4. Add players to the whitelist (see commands below)

### Client
Players need the same mod jar in their `mods/` folder. The client side only handles the handshake — no configuration needed.

## Configuration

`logindaddy.properties` is generated in the server root on first launch.

```properties
# sqlite (local file) or mysql
database.type=sqlite

# MySQL only — ignored if using sqlite
mysql.host=localhost
mysql.port=3306
mysql.database=logindaddy
mysql.username=root
mysql.password=
```

><h3>⚠️ Never commit "logindaddy.properties" to version control or show it to anyone!</h3><h3><B>It contains your database credentials.</B></h3>

## Commands

All admin commands require the server console (no in-game access).

| Command | Description |
|---|---|
| `logindaddy whitelist add <username>` | Add a player to the whitelist |
| `logindaddy whitelist remove <username>` | Remove a player from the whitelist |
| `resetpassword <username> <newpassword>` | Reset a player's password |

Player commands (in-game, only usable during authentication):

| Command | Description |
|---|---|
| `/register <password>` | Register with a password (first time only) |
| `/login <password>` | Log in with your password |
| `/changepassword <old> <new>` | Change your password (must be logged in) |

## Building

```bash
./gradlew build
```

Output jar will be in `build/libs/`.

## License

MIT