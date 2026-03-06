# Login Daddy
A Fabric mod for private friend servers (Mainly Offline-Mode servers). Requires players to have the mod installed on their client, be on the whitelist of this mod (not the server whitelist) and authenticate with a password before they can play.

## How it works
1. Player connects → sent to an invisible limbo dimension
2. Server checks if the client has LoginDaddy installed (handshake). No mod = kicked
3. Client is prompted to enter the server key (if one is configured). Wrong key = kicked
4. Server checks if the player is whitelisted. Not whitelisted = kicked
5. If the player has a valid session (logged in within the last 48 hours from the same IP), they are released automatically
6. Otherwise, player registers or logs in with a password
7. Player is teleported back to where they joined from

Join/leave messages and tab list are hidden until authentication is complete.

## Requirements
- Minecraft 1.21.11
- Fabric Loader 0.18.2+
- Fabric API

## Setup
### Server
1. Drop the mod jar into your `mods/` folder
2. Start the server once to generate `config/LoginDaddy/logindaddy.properties`
3. Edit `logindaddy.properties` with your database credentials and optionally a server key
4. Add players to the whitelist (see commands below)

### Client
Players need the same mod jar in their `mods/` folder. On first join to a server with a key configured, a prompt will appear to enter the key. The key is saved automatically and reused on future joins.

## Configuration
`config/LoginDaddy/logindaddy.properties` is generated on first launch.

```properties
# sqlite (local file) or mysql
database.type=sqlite

# MySQL only — ignored if using sqlite
mysql.host=localhost
mysql.port=3306
mysql.database=logindaddy
mysql.username=root
mysql.password=

# Optional server key — leave empty to disable
# Players will be prompted to enter this key on first join
server.key=
```

> <h3>⚠️ Never share your <code>logindaddy.properties</code> file with anyone.</h3><h3><b>It contains your database credentials and server key.</b></h3>

## Data Files
All mod data is stored under `config/LoginDaddy/`:

| File | Description |
|---|---|
| `logindaddy.properties` | Server configuration |
| `logindaddy.db` | SQLite database (only if using SQLite) |
| `sessions.json` | Active login sessions (server-side) |

**Client-side** data is stored under the client's `config/LoginDaddy/`:

| File | Description |
|---|---|
| `serverlist.json` | Saved server keys per server address |

## Commands
Admin commands require operator permission or the server console.

| Command | Description |
|---|---|
| `logindaddy whitelist add <username>` | Add a player to the whitelist |
| `logindaddy whitelist remove <username>` | Remove a player from the whitelist |
| `logindaddy whitelist list` | List all whitelisted players |
| `resetpassword <username> <newpassword>` | Reset a player's password |

Player commands (in-game, only usable during authentication):

| Command | Description |
|---|---|
| `/register <password>` | Register with a password (first time only) |
| `/login <password>` | Log in with your password |
| `/changepassword <old> <new>` | Change your password (must be logged in) |

## License
MIT